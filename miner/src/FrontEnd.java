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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import duckutil.TaskMaster;

public class FrontEnd
{
  private HttpServer server;

  public FrontEnd(Config config)
		throws Exception
  {

    config.require("port");
    int port = config.getInt("port");

    server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", new GeneralHandler());
  }

  public void start()
    throws java.io.IOException
  {
    server.start();
  }

  public abstract class GeneralHandler implements HttpHandler
  {
    @Override
    public void handle(HttpExchange t) throws IOException {

      t.getResponseHeaders().add("Content-Language", "en-US");
      ByteArrayOutputStream b_out = new ByteArrayOutputStream();
      PrintStream print_out = new PrintStream(b_out);
      try
      {
        addHeader(print_out);
        innerHandle(print_out);
        addFooter(print_out);
      }
      catch(Throwable e)
      {
        print_out.println("Exception: " + e);
      }

      byte[] data = b_out.toByteArray();
      t.sendResponseHeaders(200, data.length);
      OutputStream out = t.getResponseBody();
      out.write(data);
      out.close();

    }

    private void addHeader(PrintStream out)
    {
      out.println("<html>");
      out.println("<head>");
      out.println("<title> explorer</title>");
      out.println("<link rel='stylesheet' type='text/css' href='https://snowblossom.org/style-fixed.css' />");
      out.println("<link REL='SHORTCUT ICON' href='https://snowblossom.org/snow-icon.png' />");
      out.println("</head>");
      out.println("<body>");
      out.print("<a href='/'>House</a>");
      out.print("<form name='query' action='/' method='GET'>");
      out.print("<input type='text' name='search' size='45' value=''>");
      out.println("<input type='submit' value='Search'>");
      out.println("<br>");
    }
    private void innerHandle(PrintStream out)
    {
      out.println("daskldfjadsj");
      out.println("fasdasfasfasadsfa");
    }
    private void addFooter(PrintStream out)
    {
      out.println("</body>");
      out.println("</html>");
    }

    public abstract void innerHandle(HttpExchange t, PrintStream out) throws Exception;
  }

}
