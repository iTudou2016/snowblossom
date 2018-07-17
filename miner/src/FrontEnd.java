package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;

import io.grpc.Server;
import io.grpc.ServerBuilder;


import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.TreeMap;
import java.util.List;

public class FrontEnd
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");
  
  public static void main(String args[]) throws Exception
  {
     if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: FrontEnd <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    FrontEnd webfe = new FrontEnd(config);

    webfe.loop();
  }

  private volatile Block last_block_template;

  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;

  private final NetworkParams params;

  private AtomicLong op_count = new AtomicLong(0L);
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private TimeRecord time_record;
  private MiningPoolServiceAgent agent;

  private ShareManager share_manager;
  private DB db;
  private ReportManager report_manager;

  public FrontEnd(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting MrPlow version %s", Globals.VERSION));

    config.require("node_host");
    config.require("pool_address");
    config.require("pool_fee");
    config.require("db_type");
    config.require("db_path");
    
    params = NetworkParams.loadFromConfig(config);

    double pool_fee = config.getDouble("pool_fee");
    double duck_fee = config.getDoubleWithDefault("pay_the_duck", 0.0);

    TreeMap<String, Double> fixed_fee_map = new TreeMap<>();
    fixed_fee_map.put( AddressUtil.getAddressString(params.getAddressPrefix(), getPoolAddress()), pool_fee );
    if (duck_fee > 0.0)
    {
      fixed_fee_map.put( "snow:crqls8qkumwg353sfgf5kw2lw2snpmhy450nqezr", duck_fee);
    }
    loadDB();
    PPLNSState pplns_state = null;
    try
    {
      pplns_state = PPLNSState.parseFrom(db.getSpecialMap().get("pplns_state"));
      logger.info(String.format("Loaded PPLNS state with %d entries", pplns_state.getShareEntriesCount()));
    }
    catch(Throwable t)
    {
      logger.log(Level.WARNING, "Unable to load PPLNS state, starting fresh:" + t);
    }

    share_manager = new ShareManager(fixed_fee_map, pplns_state);
    report_manager = new ReportManager();

  }
  private void loadDB()
    throws Exception
  {
    String db_type = config.get("db_type");

    if(db_type.equals("rocksdb"))
    {
      db = new JRocksDB(config);
    }
    else if (db_type.equals("lobstack"))
    {
      db = new LobstackDB(config);
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }

  private void loop()
    throws Exception
  {
    long last_report = System.currentTimeMillis();

    while (true)
    {
      Thread.sleep(10000);
      printStats();
     }
   }

  private void saveState()
  {
    PPLNSState state = share_manager.getState();
    db.getSpecialMap().put("pplns_state", state.toByteString());
  }

  private AddressSpecHash getPoolAddress() throws Exception
  {
      String address = config.get("pool_address");
      AddressSpecHash to_addr = new AddressSpecHash(address, params);
      return to_addr;
  }

  public void stop()
  {
    terminate = true;
  }

  private volatile boolean terminate = false;

  public NetworkParams getParams() {return params;}

  public UserServiceBlockingStub getBlockingStub(){return blockingStub;}
  public ShareManager getShareManager(){return share_manager;}
  public ReportManager getReportManager(){return report_manager;}

  public void printStats()
  {

    DecimalFormat df = new DecimalFormat("0.000");
    logger.info(String.format("Mining rate: %s", report_manager.getTotalRate().getReportLong(df)));

  }

}
