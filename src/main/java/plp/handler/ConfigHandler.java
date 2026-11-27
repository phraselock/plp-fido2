package plp.handler;

import com.ipoxo.plcore.lib.Log;
import io.javalin.config.JavalinConfig;
import plp.backend.PLPApplication;
import plp.lib.ConfigPathResolver;
import plp.ui.ConfigPage;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigHandler
{
  private static final String FILE = "application.properties";

  public void registerRoutes(JavalinConfig config)
  {
    String postUrl = PLPApplication.PATH_PREFIX + "/admin/appconfig";

    config.routes.get("/admin/appconfig", ctx ->
    {
      ctx.contentType("text/html; charset=UTF-8");
      ctx.result(ConfigPage.renderEditor(
        "Application", FILE, postUrl, "appconfig",
        readFile(FILE), List.of(), false));
    });

    config.routes.post("/admin/appconfig", ctx ->
    {
      String content = ctx.formParam("content");
      List<String> w = new ArrayList<>();
      boolean saved  = false;

      if (content != null)
      {
        w.addAll(validateApplication(parse(content)));
        writeFile(FILE, content);
        saved = true;
        Log.i("[ConfigHandler] application.properties saved");
      }

      ctx.contentType("text/html; charset=UTF-8");
      ctx.result(ConfigPage.renderEditor(
        "Application", FILE, postUrl, "appconfig",
        content != null ? content : "", w, saved));
    });
  }

  // ── File I/O ──────────────────────────────────────────────────────────────

  private String readFile(String filename)
  {
    Path path = ConfigPathResolver.resolve(filename, ConfigHandler.class);
    try
    {
      if (Files.exists(path)) return Files.readString(path);
    }
    catch (Exception e) { Log.e("[ConfigHandler] Read failed: " + e.getMessage()); }
    return "";
  }

  private void writeFile(String filename, String content)
  {
    Path path = ConfigPathResolver.resolve(filename, ConfigHandler.class);
    try { Files.writeString(path, content); }
    catch (Exception e) { Log.e("[ConfigHandler] Write failed: " + e.getMessage()); }
  }

  // ── Parsing ───────────────────────────────────────────────────────────────

  private Properties parse(String content)
  {
    Properties p = new Properties();
    try { p.load(new StringReader(content)); }
    catch (Exception ignored) {}
    return p;
  }

  // ── Validation ────────────────────────────────────────────────────────────

  private List<String> validateApplication(Properties p)
  {
    List<String> w = new ArrayList<>();

    String port = p.getProperty("server.port", "").trim();
    if (!port.matches("\\d+") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535)
      w.add("server.port: not a valid port number (1–65535)");

    String ips = p.getProperty("allowed.ips", "").trim();
    if (ips.isBlank())
      w.add("allowed.ips: no IPs configured — all requests will be rejected");

    String minT = p.getProperty("jetty.minThreads", "").trim();
    String maxT = p.getProperty("jetty.maxThreads", "").trim();
    if (minT.matches("\\d+") && maxT.matches("\\d+") && Integer.parseInt(minT) > Integer.parseInt(maxT))
      w.add("jetty.minThreads (" + minT + ") > jetty.maxThreads (" + maxT + ")");

    if (p.getProperty("admin.token", "").trim().isEmpty())
      w.add("admin.token: not set — /admin/* routes are unprotected");

    String prefix = p.getProperty("app.path.prefix", "").trim();
    if (!prefix.isEmpty() && (!prefix.startsWith("/") || prefix.endsWith("/")))
      w.add("app.path.prefix: must start with '/' and must not end with '/' (e.g. /webauthn)");

    String sc = p.getProperty("webauthn.signcount.check", "true").trim().toLowerCase();
    if (!sc.equals("true") && !sc.equals("false"))
      w.add("webauthn.signcount.check: expected 'true' or 'false', got: \"" + sc + "\"");

    return w;
  }
}
