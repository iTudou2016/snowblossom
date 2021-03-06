package snowblossom.miner;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;
import java.util.Set;
import java.util.Map;
import java.nio.channels.FileChannel;

import java.nio.ByteBuffer;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;
import java.util.logging.Logger;
import duckutil.Config;

import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import com.google.protobuf.ByteString;

import org.junit.Assert;

import snowblossom.mining.proto.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import snowblossom.mining.proto.SharedMiningServiceGrpc.SharedMiningServiceBlockingStub;

public class FieldSourceRemote extends FieldSource implements BatchSource
{
  private ThreadLocal<SharedMiningServiceBlockingStub> stub_local=new ThreadLocal<>();
  SharedMiningServiceBlockingStub stub_one;

  public FieldSourceRemote(Config config, int layer)
  {
    config.require("layer_" + layer + "_host");
    config.require("layer_" + layer + "_range");

    List<String> range_lst = config.getList("layer_" + layer + "_range");
    if (range_lst.size() != 2)
    {
      throw new RuntimeException("Expected range of two numbers.  Example: 0,17");
    }
    int start = Integer.parseInt(range_lst.get(0));
    int end = Integer.parseInt(range_lst.get(1));

    Assert.assertTrue(end > start);
    Assert.assertTrue(start >= 0);

    TreeSet<Integer> holding = new TreeSet<>();
    for(int x = start; x<end; x++)
    {
      holding.add(x);
    }
    holding_set = ImmutableSet.copyOf(holding);

    stub_host = config.get("layer_" + layer + "_host");
    stub_port = 2311;

    // In cloud testing, using stub per thread got 611kh/s vs 575kh/s with a single stub
    // otherwise same setup
    //ManagedChannel channel = ManagedChannelBuilder.forAddress(stub_host, stub_port).usePlaintext(true).build();
    //stub_one = SharedMiningServiceGrpc.newBlockingStub(channel);
  }

  private String stub_host;
  private int stub_port;

  protected SharedMiningServiceBlockingStub getStub()
  {
    if (stub_one != null) return stub_one;

    SharedMiningServiceBlockingStub stub = stub_local.get();
    if (stub == null)
    {
      ManagedChannel channel = ManagedChannelBuilder.forAddress(stub_host, stub_port).usePlaintext(true).build();
      stub = SharedMiningServiceGrpc.newBlockingStub(channel);
      stub_local.set(stub);
    }
    return stub;
  }

  @Override
  public void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    if (bb.remaining() != Globals.SNOW_MERKLE_HASH_LEN)
    {
      throw new RuntimeException("FieldSourceRemote can't handle request for " + bb.remaining());
    }
    ByteString bs = readWordsBulk(ImmutableList.of(word_index)).get(0);
    bb.put(bs.toByteArray());
  } 

  /** Shouldn't use this much, just allowed here for proof generation */
  @Override
  public List<ByteString> readWordsBulk(List<Long> indexes)
  {
    return getStub().getWords(GetWordsRequest.newBuilder().addAllWordIndexes(indexes).build()).getWordsList();
  }


  @Override
  public boolean skipQueueOnRehit()
  {
    return false;
  }

}
