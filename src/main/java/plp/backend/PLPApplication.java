package plp.backend;

import com.ipoxo.plcore.lib.Log;
import io.javalin.Javalin;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import plp.handler.AdminHandler;
import plp.handler.WebAuthnHandler;
import plp.lib.ConfigPathResolver;
import plp.lib.DB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class PLPApplication
{
  private static final String PROPERTIES = "application.properties";

  private static final Properties  CONFIG      = loadConfig();
  private static final Set<String> ALLOWED_IPS = parseAllowedIps(CONFIG);
  private static final int         PORT        = Integer.parseInt(CONFIG.getProperty("server.port", "8080"));
  private static final int         MAX_THREADS = Integer.parseInt(CONFIG.getProperty("jetty.maxThreads", "10"));
  private static final int         MIN_THREADS = Integer.parseInt(CONFIG.getProperty("jetty.minThreads", "2"));

  // External URL prefix used by nginx (e.g. "/webauthn"). Empty when no proxy.
  public static final String PATH_PREFIX = CONFIG.getProperty("app.path.prefix", "").trim();

  private static Properties loadConfig()
  {
    Properties props = new Properties();
    try (InputStream in = PLPApplication.class.getResourceAsStream("/" + PROPERTIES))
    {
      if (in != null) props.load(in);
    }
    catch (IOException e) { throw new RuntimeException("Failed to load " + PROPERTIES, e); }

    Path external = ConfigPathResolver.resolve(PROPERTIES, PLPApplication.class);
    if (Files.exists(external))
    {
      try (InputStream in = Files.newInputStream(external)) { props.load(in); }
      catch (IOException e) { throw new RuntimeException("Failed to load " + external.toAbsolutePath(), e); }
      Log.i("[plp-fido2] Config: " + external.toAbsolutePath());
    }
    return props;
  }

  private static Set<String> parseAllowedIps(Properties props)
  {
    return Arrays.stream(props.getProperty("allowed.ips", "").split(","))
      .map(String::trim).filter(ip -> !ip.isEmpty())
      .collect(Collectors.toSet());
  }

  private static String adminToken()
  {
    Properties p = new Properties();
    try (InputStream in = PLPApplication.class.getResourceAsStream("/" + PROPERTIES))
    {
      if (in != null) p.load(in);
    }
    catch (Exception ignored) {}
    Path external = ConfigPathResolver.resolve(PROPERTIES, PLPApplication.class);
    try (InputStream in = Files.newInputStream(external)) { p.load(in); }
    catch (Exception ignored) {}
    return p.getProperty("admin.token", "").trim();
  }

  private static Javalin web;

  public static void stop()
  {
    if (web != null) web.stop();
  }

  public static void start()
  {

    var threadPool = new QueuedThreadPool(MAX_THREADS, MIN_THREADS, 60_000);
    threadPool.setName("jetty");

    web = Javalin.create(config ->
    {
      config.jetty.threadPool = threadPool;

      config.staticFiles.add(sf ->
      {
        sf.hostedPath = "/js";
        sf.directory  = "/public/js";
      });
      config.staticFiles.add(sf ->
      {
        sf.hostedPath = "/css";
        sf.directory  = "/public/css";
      });
      config.staticFiles.add(sf ->
      {
        sf.hostedPath = "/img";
        sf.directory  = "/public/img";
      });

      config.routes.before(ctx ->
      {
        if (!ALLOWED_IPS.contains(ctx.ip()))
        {
          ctx.status(403).result("Forbidden");
          ctx.skipRemainingHandlers();
          return;
        }

        if (ctx.path().startsWith("/admin/"))
        {
          String token = adminToken();
          if (token.isEmpty() || token.equals(ctx.queryParam("token"))) return;
          ctx.status(403).result("Admin token required");
          ctx.skipRemainingHandlers();
        }
      });

      new WebAuthnHandler().registerRoutes(config);
      new AdminHandler().registerRoutes(config);
    });
    web.start(PORT);

    Log.i("[plp-fido2] Started on port " + PORT);
  }
}
