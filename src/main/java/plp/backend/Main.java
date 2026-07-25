package plp.backend;

import com.ipoxo.plcore.lib.Log;

public class Main
{
  public static void main(String[] args)
  {
    Runtime.getRuntime().addShutdownHook(new Thread(() ->
    {
      Log.i("[Shutdown] Stopping...");
      PLPApplication.stop();
      Log.i("[Shutdown] Done.");
    }, "shutdown"));

    PLPApplication.start();
  }
}
