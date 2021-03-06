/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.util;

import org.apache.ratis.protocol.*;
import org.apache.ratis.shaded.com.google.protobuf.ByteString;
import org.apache.ratis.shaded.com.google.protobuf.ServiceException;
import org.apache.ratis.shaded.proto.RaftProtos.*;
import org.apache.ratis.shaded.proto.RaftProtos.LogEntryProto.LogEntryBodyCase;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface ProtoUtils {
  static ByteString writeObject2ByteString(Object obj) {
    final ByteString.Output byteOut = ByteString.newOutput();
    try(final ObjectOutputStream objOut = new ObjectOutputStream(byteOut)) {
      objOut.writeObject(obj);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Unexpected IOException when writing an object to a ByteString.", e);
    }
    return byteOut.toByteString();
  }

  static Object toObject(ByteString bytes) {
    try(final ObjectInputStream in = new ObjectInputStream(bytes.newInput())) {
      return in.readObject();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Unexpected IOException when reading an object from a ByteString.", e);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  static ByteString toByteString(String string) {
    return ByteString.copyFromUtf8(string);
  }

  static ByteString toByteString(byte[] bytes) {
    return toByteString(bytes, 0, bytes.length);
  }

  static ByteString toByteString(byte[] bytes, int offset, int size) {
    // return singleton to reduce object allocation
    return bytes.length == 0 ?
        ByteString.EMPTY : ByteString.copyFrom(bytes, offset, size);
  }

  static RaftPeerProto toRaftPeerProto(RaftPeer peer) {
    RaftPeerProto.Builder builder = RaftPeerProto.newBuilder()
        .setId(peer.getId().toByteString());
    if (peer.getAddress() != null) {
      builder.setAddress(peer.getAddress());
    }
    return builder.build();
  }

  static RaftPeer toRaftPeer(RaftPeerProto p) {
    return new RaftPeer(RaftPeerId.valueOf(p.getId()), p.getAddress());
  }

  static RaftPeer[] toRaftPeerArray(List<RaftPeerProto> protos) {
    final RaftPeer[] peers = new RaftPeer[protos.size()];
    for (int i = 0; i < peers.length; i++) {
      peers[i] = toRaftPeer(protos.get(i));
    }
    return peers;
  }

  static Iterable<RaftPeerProto> toRaftPeerProtos(
      final Collection<RaftPeer> peers) {
    return () -> new Iterator<RaftPeerProto>() {
      final Iterator<RaftPeer> i = peers.iterator();

      @Override
      public boolean hasNext() {
        return i.hasNext();
      }

      @Override
      public RaftPeerProto next() {
        return toRaftPeerProto(i.next());
      }
    };
  }

  static RaftGroupId toRaftGroupId(RaftGroupIdProto proto) {
    return RaftGroupId.valueOf(proto.getId());
  }

  static RaftGroupIdProto.Builder toRaftGroupIdProtoBuilder(RaftGroupId id) {
    return RaftGroupIdProto.newBuilder().setId(id.toByteString());
  }

  static RaftGroup toRaftGroup(RaftGroupProto proto) {
    return new RaftGroup(toRaftGroupId(proto.getGroupId()),
        toRaftPeerArray(proto.getPeersList()));
  }

  static RaftGroupProto.Builder toRaftGroupProtoBuilder(RaftGroup group) {
    return RaftGroupProto.newBuilder()
        .setGroupId(toRaftGroupIdProtoBuilder(group.getGroupId()))
        .addAllPeers(toRaftPeerProtos(group.getPeers()));
  }

  static CommitInfoProto toCommitInfoProto(RaftPeer peer, long commitIndex) {
    return CommitInfoProto.newBuilder()
        .setServer(toRaftPeerProto(peer))
        .setCommitIndex(commitIndex)
        .build();
  }

  static void addCommitInfos(Collection<CommitInfoProto> commitInfos, Consumer<CommitInfoProto> adder) {
    if (commitInfos != null && !commitInfos.isEmpty()) {
      commitInfos.stream().forEach(i -> adder.accept(i));
    }
  }

  static String toString(CommitInfoProto proto) {
    return RaftPeerId.valueOf(proto.getServer().getId()) + ":c" + proto.getCommitIndex();
  }

  static String toString(Collection<CommitInfoProto> protos) {
    return protos.stream().map(ProtoUtils::toString).collect(Collectors.toList()).toString();
  }

  static boolean isConfigurationLogEntry(LogEntryProto entry) {
    return entry.getLogEntryBodyCase() ==
        LogEntryProto.LogEntryBodyCase.CONFIGURATIONENTRY;
  }

  static LogEntryProto toLogEntryProto(
      SMLogEntryProto operation, long term, long index,
      ClientId clientId, long callId) {
    return LogEntryProto.newBuilder().setTerm(term).setIndex(index)
        .setSmLogEntry(operation)
        .setClientId(clientId.toByteString()).setCallId(callId)
        .build();
  }

  static boolean shouldReadStateMachineData(LogEntryProto entry) {
    if (entry.getLogEntryBodyCase() != LogEntryBodyCase.SMLOGENTRY) {
      return false;
    }
    final SMLogEntryProto smLog = entry.getSmLogEntry();
    return smLog.getStateMachineDataAttached() && smLog.getStateMachineData().isEmpty();
  }

  /**
   * If the given entry is {@link LogEntryBodyCase#SMLOGENTRY} and it has state machine data,
   * build a new entry without the state machine data.
   *
   * @return a new entry without the state machine data if the given has state machine data;
   *         otherwise, return the given entry.
   */
  static LogEntryProto removeStateMachineData(LogEntryProto entry) {
    if (entry.getLogEntryBodyCase() != LogEntryBodyCase.SMLOGENTRY) {
      return entry;
    }
    final SMLogEntryProto smLog = entry.getSmLogEntry();
    if (smLog.getStateMachineData().isEmpty()) {
      return entry;
    }
    // build a new LogEntryProto without state machine data
    // and mark that it has been removed
    return LogEntryProto.newBuilder(entry)
        .setSmLogEntry
            (SMLogEntryProto.newBuilder()
            .setData(smLog.getData())
            .setStateMachineDataAttached(true)
            .setSerializedProtobufSize(entry.getSerializedSize()))
        .build();
  }

  static long getSerializedSize(LogEntryProto entry) {
    if (entry.getLogEntryBodyCase() != LogEntryBodyCase.SMLOGENTRY) {
      return entry.getSerializedSize();
    }
    final SMLogEntryProto smLog = entry.getSmLogEntry();
    if (!smLog.getStateMachineDataAttached()) {
      // if state machine data was never set, return the proto serialized size
      return entry.getSerializedSize();
    }
    return smLog.getSerializedProtobufSize();
  }

  static IOException toIOException(ServiceException se) {
    final Throwable t = se.getCause();
    if (t == null) {
      return new IOException(se);
    }
    return t instanceof IOException? (IOException)t : new IOException(se);
  }

  static String toString(RaftRpcRequestProto proto) {
    return proto.getRequestorId().toStringUtf8() + "->" + proto.getReplyId().toStringUtf8()
        + "#" + proto.getCallId();
  }

  static String toString(RaftRpcReplyProto proto) {
    return proto.getRequestorId().toStringUtf8() + "<-" + proto.getReplyId().toStringUtf8()
        + "#" + proto.getCallId() + ":"
        + (proto.getSuccess()? "OK": "FAIL");
  }
  static String toString(RequestVoteReplyProto proto) {
    return toString(proto.getServerReply()) + "-t" + proto.getTerm();
  }
  static String toString(AppendEntriesReplyProto proto) {
    return toString(proto.getServerReply()) + "-t" + proto.getTerm()
        + ", nextIndex=" + proto.getNextIndex()
        + ", result: " + proto.getResult();
  }
}
