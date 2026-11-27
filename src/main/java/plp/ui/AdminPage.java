package plp.ui;

import j2html.tags.DomContent;
import plp.backend.PLPApplication;
import plp.lib.CredentialDao;
import plp.lib.PendingUserDao;

import java.util.List;

import static j2html.TagCreator.*;

public class AdminPage
{
  private static final String P = PLPApplication.PATH_PREFIX;

  private static final String CELL          = "padding:10px 14px;border-bottom:1px solid #f0f0f0;font-size:0.88rem;vertical-align:middle;";
  private static final String HEAD          = "padding:10px 14px;font-size:0.78rem;font-weight:600;color:#666;text-transform:uppercase;letter-spacing:.5px;border-bottom:2px solid #eee;background:#fafafa;";
  private static final String BADGE_OK      = "display:inline-block;padding:2px 10px;border-radius:12px;font-size:0.75rem;background:#e6f9ee;color:#1a7a40;";
  private static final String BADGE_PENDING = "display:inline-block;padding:2px 10px;border-radius:12px;font-size:0.75rem;background:#fff8e1;color:#b07d00;";
  private static final String BTN_DEL       = "padding:4px 12px;font-size:0.8rem;cursor:pointer;border:1px solid #e0e0e0;border-radius:6px;background:#fff;color:#c0392b;margin:0;";
  private static final String BTN_EDIT      = "padding:4px 12px;font-size:0.8rem;cursor:pointer;border:1px solid #e0e0e0;border-radius:6px;background:#fff;color:#4a6cf7;margin:0;";
  private static final String BTN_SAVE      = "padding:4px 12px;font-size:0.8rem;cursor:pointer;border:none;border-radius:6px;background:#4a6cf7;color:#fff;margin:0;";
  private static final String BTN_CANCEL    = "padding:4px 10px;font-size:0.8rem;cursor:pointer;border:1px solid #ddd;border-radius:6px;background:#fff;color:#666;margin:0;";

  // ── Pages ─────────────────────────────────────────────────────────────────

  public static String renderUsers(List<PendingUserDao.PendingUser> users)
  {
    return page("FIDO2 Admin", addUserCard(), usersCard(users));
  }

  public static String renderUser(PendingUserDao.PendingUser u,
                                  List<CredentialDao.CredentialSummary> credentials)
  {
    return page(u.email() + " — FIDO2 Admin", userCard(u), credentialsCard(u, credentials));
  }

  private static String page(String pageTitle, DomContent... cards)
  {
    return document(html(
      head(
        meta().withCharset("UTF-8"),
        title(pageTitle),
        style(CSS + AdminNav.CSS)
      ),
      body(
        div().withStyle(AdminNav.FRAME).with(
          AdminNav.bar("users"),
          div().withClass("content").with(cards)
        ),
        AdminNav.footer(),
        script(rawHtml(JS))
      )
    ));
  }

  // ── User list ─────────────────────────────────────────────────────────────

  private static DomContent addUserCard()
  {
    return div().withClass("card").with(
      h2("Add User"),
      form()
        .withAction(P + "/admin/users/create")
        .withMethod("post")
        .withStyle("display:flex;align-items:center;gap:0;")
        .with(
          input().withType("email").withName("email")
            .withPlaceholder("user@example.com")
            .attr("required", ""),
          button("Create").withType("submit").withClass("btn-primary")
        )
    );
  }

  private static DomContent usersCard(List<PendingUserDao.PendingUser> users)
  {
    return div().withClass("card").with(
      h2("Users"),
      input().withType("text").withClass("search-input")
        .withPlaceholder("Search…")
        .attr("oninput", "filterTable(this,'tbody-users')"),
      users.isEmpty()
        ? p("No users yet.").withClass("empty")
        : table(
            thead(tr(
              th("E-Mail").withStyle(HEAD),
              th("Keys").withStyle(HEAD),
              th("Registration code").withStyle(HEAD),
              th("").withStyle(HEAD)
            )),
            tbody().withId("tbody-users").with(
              each(users, u -> tr(
                td(a(u.email()).withHref(userUrl(u)).withClass("user-link")).withStyle(CELL),
                td(String.valueOf(u.keyCount())).withStyle(CELL),
                td(codeCell(u)).withStyle(CELL),
                td(
                  div().withStyle("display:flex;align-items:center;gap:6px;flex-wrap:wrap;").with(
                    a("Keys").withHref(userUrl(u)).withStyle(BTN_EDIT + "text-decoration:none;"),
                    newCodeForm(u, "list"),
                    deleteUserForm(u)
                  )
                ).withStyle(CELL)
              ))
            )
          )
    );
  }

  // ── User detail ───────────────────────────────────────────────────────────

  private static DomContent userCard(PendingUserDao.PendingUser u)
  {
    return div().withClass("card").with(
      h2(u.email()),
      table(
        tr(td("Keys").withStyle(CELL + "width:200px;color:#666;"),
           td(String.valueOf(u.keyCount())).withStyle(CELL)),
        tr(td("Registration code").withStyle(CELL + "color:#666;"),
           td(codeCell(u)).withStyle(CELL))
      ),
      div().withStyle("display:flex;gap:6px;margin-top:16px;").with(
        newCodeForm(u, "user"),
        deleteUserForm(u)
      )
    );
  }

  private static DomContent credentialsCard(PendingUserDao.PendingUser u,
                                            List<CredentialDao.CredentialSummary> credentials)
  {
    return div().withClass("card").with(
      h2("Registered Keys"),
      credentials.isEmpty()
        ? p("No keys registered yet.").withClass("empty")
        : table(
            thead(tr(
              th("Display Name").withStyle(HEAD),
              th("Authenticator").withStyle(HEAD),
              th("Domain").withStyle(HEAD),
              th("Registered").withStyle(HEAD),
              th("").withStyle(HEAD)
            )),
            tbody().with(
              each(credentials, c -> tr(
                td(
                  span(c.displayName() != null ? c.displayName() : "").withClass("dn-text"),
                  input().withType("text").withClass("dn-input")
                    .withValue(c.displayName() != null ? c.displayName() : "")
                    .withStyle("display:none")
                ).withStyle(CELL),
                td(c.authenticator() != null ? c.authenticator() : "—").withStyle(CELL),
                td(c.domain() != null ? c.domain() : "—").withStyle(CELL),
                td(formatDate(c.createdAt())).withStyle(CELL),
                td(
                  div().withStyle("display:flex;align-items:center;gap:6px;flex-wrap:wrap;").with(
                    button("Edit").withType("button")
                      .withClass("btn-edit")
                      .withStyle(BTN_EDIT)
                      .attr("onclick", "startEdit(this)"),
                    div().withClass("edit-actions").withStyle("display:none;align-items:center;gap:6px;").with(
                      form()
                        .withAction(P + "/admin/credential/rename")
                        .withMethod("post")
                        .withClass("form-rename")
                        .withStyle("display:contents")
                        .with(
                          input().withType("hidden").withName("id").withValue(String.valueOf(c.id())),
                          input().withType("hidden").withName("user").withValue(String.valueOf(u.id())),
                          input().withType("hidden").withName("displayName").withClass("dn-value"),
                          button("Save").withType("submit").withStyle(BTN_SAVE)
                        ),
                      button("Cancel").withType("button")
                        .withStyle(BTN_CANCEL)
                        .attr("onclick", "cancelEdit(this)")
                    ),
                    form()
                      .withAction(P + "/admin/credential/delete")
                      .withMethod("post")
                      .withStyle("display:contents")
                      .attr("onsubmit", "return confirm('Delete this key?')")
                      .with(
                        input().withType("hidden").withName("id").withValue(String.valueOf(c.id())),
                        input().withType("hidden").withName("user").withValue(String.valueOf(u.id())),
                        button("Delete").withType("submit").withStyle(BTN_DEL)
                      )
                  )
                ).withStyle(CELL)
              ))
            )
          )
    );
  }

  // ── Shared parts ──────────────────────────────────────────────────────────

  // Pending: code is shown until a key has been registered with it.
  private static DomContent codeCell(PendingUserDao.PendingUser u)
  {
    return u.used()
      ? span("Used").withStyle(BADGE_OK)
      : span(span(u.regToken()).withClass("token"), span(" Pending").withStyle(BADGE_PENDING + "margin-left:8px;"));
  }

  private static DomContent newCodeForm(PendingUserDao.PendingUser u, String back)
  {
    return form()
      .withAction(P + "/admin/users/newcode")
      .withMethod("post")
      .withStyle("display:contents")
      .with(
        input().withType("hidden").withName("id").withValue(String.valueOf(u.id())),
        input().withType("hidden").withName("back").withValue(back),
        button("New code").withType("submit").withStyle(BTN_EDIT)
      );
  }

  private static DomContent deleteUserForm(PendingUserDao.PendingUser u)
  {
    return form()
      .withAction(P + "/admin/users/delete")
      .withMethod("post")
      .withStyle("display:contents")
      .attr("onsubmit", "return confirm('Delete this user and all registered keys?')")
      .with(
        input().withType("hidden").withName("id").withValue(String.valueOf(u.id())),
        button("Delete").withType("submit").withStyle(BTN_DEL)
      );
  }

  private static String userUrl(PendingUserDao.PendingUser u)
  {
    return P + "/admin/user?id=" + u.id();
  }

  private static String formatDate(long epochSeconds)
  {
    if (epochSeconds <= 0) return "—";
    return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      .withZone(java.time.ZoneId.systemDefault())
      .format(java.time.Instant.ofEpochSecond(epochSeconds));
  }

  private static final String CSS =
    "*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }" +
    "body { font-family: Arial, Helvetica, sans-serif; background: #f5f5f5; padding: 32px; }" +
    "h2 { font-size: 1rem; color: #1a1a2e; margin-bottom: 16px; }" +
    ".card { background: #fff; border-radius: 12px; border: 1px solid #eee;" +
    "        padding: 24px; margin-bottom: 24px; }" +
    ".content .card:last-child { margin-bottom: 0; }" +
    ".user-link { color:#1a1a2e; text-decoration:none; }" +
    ".user-link:hover { text-decoration:underline; }" +
    "table { width: 100%; border-collapse: collapse; }" +
    "input[type=email] {" +
    "  padding: 8px 12px; border: 1px solid #ddd; border-radius: 8px;" +
    "  font-size: 0.9rem; width: 320px; background: #fafafa; outline: none; }" +
    "input:focus { border-color: #4a6cf7; background: #fff; }" +
    ".btn-primary { padding:8px 20px; background:#4a6cf7; color:#fff; border:none;" +
    "  border-radius:8px; font-size:0.9rem; cursor:pointer; margin-left:10px; }" +
    ".btn-primary:hover { background:#3a5ce0; }" +
    ".token { font-family: monospace; font-size: 0.95rem; font-weight: 600;" +
    "         letter-spacing: 2px; color: #1a1a2e; }" +
    ".empty { color: #aaa; font-size: 0.85rem; padding: 16px 14px; }" +
    ".search-input { display:block; width:100%; padding:8px 12px; border:1px solid #ddd;" +
    "  border-radius:8px; font-size:0.88rem; margin-bottom:16px; background:#fafafa; outline:none; }" +
    ".search-input:focus { border-color:#4a6cf7; background:#fff; }" +
    ".dn-input { padding:4px 8px; border:1px solid #4a6cf7; border-radius:6px;" +
    "  font-size:0.88rem; width:200px; outline:none; }";

  private static final String JS =
    // full-text search — filters tbody rows
    "function filterTable(inp,tbodyId){" +
    "  var q=inp.value.toLowerCase();" +
    "  document.querySelectorAll('#'+tbodyId+' tr').forEach(function(r){" +
    "    r.style.display=r.textContent.toLowerCase().includes(q)?'':'none';" +
    "  });" +
    "}" +
    // inline display-name edit
    "function startEdit(btn){" +
    "  var row=btn.closest('tr');" +
    "  row.querySelector('.dn-text').style.display='none';" +
    "  row.querySelector('.dn-input').style.removeProperty('display');" +
    "  btn.style.display='none';" +
    "  var ea=row.querySelector('.edit-actions');" +
    "  ea.style.display='flex';" +
    "}" +
    "function cancelEdit(btn){" +
    "  var row=btn.closest('tr');" +
    "  var inp=row.querySelector('.dn-input');" +
    "  inp.value=row.querySelector('.dn-text').textContent.trim();" +
    "  inp.style.display='none';" +
    "  row.querySelector('.dn-text').style.removeProperty('display');" +
    "  row.querySelector('.btn-edit').style.removeProperty('display');" +
    "  row.querySelector('.edit-actions').style.display='none';" +
    "}" +
    // copy visible input value into hidden field before rename form submits
    "document.querySelectorAll('.form-rename').forEach(function(f){" +
    "  f.addEventListener('submit',function(){" +
    "    f.querySelector('.dn-value').value=" +
    "      f.closest('tr').querySelector('.dn-input').value.trim();" +
    "  });" +
    "});";
}
