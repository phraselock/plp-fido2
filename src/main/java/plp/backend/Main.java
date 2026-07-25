package plp.backend;

import com.ipoxo.plcore.lib.Log;
import plp.lib.DB;

public class Main
{
  public static void main(String[] args)
  {
    DB.initDB();

    Runtime.getRuntime().addShutdownHook(new Thread(() ->
    {
      Log.i("[Shutdown] Stopping...");
      PLPApplication.stop();
      Log.i("[Shutdown] Done.");
    }, "shutdown"));


    PLPApplication.start();

  }
}
