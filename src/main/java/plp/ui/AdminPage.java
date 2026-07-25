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

  public static String render(List<PendingUserDao.PendingUser> pending,
                               List<CredentialDao.CredentialSummary> credentials)
  {
    return document(html(
      head(
        meta().withCharset("UTF-8"),
        title("FIDO2 Admin"),
        style(CSS)
      ),
      body(
        div().withStyle("max-width:1024px;margin:0 auto;").with(
          AdminNav.bar(),
          tabBar(),
          div().withId("tab-users").withClass("tab-content").with(
            addUserCard(),
            pendingUsersCard(pending)
          ),
          div().withId("tab-credentials").withClass("tab-content").withStyle("display:none").with(
            credentialsCard(credentials)
          ),
          AdminNav.footer()
        ),
        script(rawHtml(JS))
      )
    ));
  }

  private static DomContent tabBar()
  {
    return div().withClass("tab-bar").with(
      button("Users")
        .withClass("tab-btn active")
        .attr("data-tab", "users")
        .attr("onclick", "switchTab('users')"),
      button("Credentials")
        .withClass("tab-btn")
        .attr("data-tab", "credentials")
        .attr("onclick", "switchTab('credentials')")
    );
  }

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

  private static DomContent pendingUsersCard(List<PendingUserDao.PendingUser> pending)
  {
    return div().withClass("card").with(
      h2("Pre-registered Users"),
      input().withType("text").withClass("search-input")
        .withPlaceholder("Search…")
        .attr("oninput", "filterTable(this,'tbody-users')"),
      pending.isEmpty()
        ? p("No users yet.").withClass("empty")
        : table(
            thead(tr(
              th("E-Mail").withStyle(HEAD),
              th("Token").withStyle(HEAD),
              th("Status").withStyle(HEAD),
              th("").withStyle(HEAD)
            )),
            tbody().withId("tbody-users").with(
              each(pending, u -> tr(
                td(u.email()).withStyle(CELL),
                td(span(u.regToken()).withClass("token")).withStyle(CELL),
                td(u.used()
                  ? span("Registered").withStyle(BADGE_OK)
                  : span("Pending").withStyle(BADGE_PENDING)
                ).withStyle(CELL),
                td(
                  form()
                    .withAction(P + "/admin/users/delete")
                    .withMethod("post")
                    .with(
                      input().withType("hidden").withName("id")
                        .withValue(String.valueOf(u.id())),
                      button("Delete").withType("submit").withStyle(BTN_DEL)
                    )
                ).withStyle(CELL)
              ))
            )
          )
    );
  }

  private static DomContent credentialsCard(List<CredentialDao.CredentialSummary> credentials)
  {
    return div().withClass("card").with(
      h2("Registered Credentials"),
      input().withType("text").withClass("search-input")
        .withPlaceholder("Search…")
        .attr("oninput", "filterTable(this,'tbody-creds')"),
      credentials.isEmpty()
        ? p("No credentials registered yet.").withClass("empty")
        : table(
            thead(tr(
              th("User").withStyle(HEAD),
              th("Display Name").withStyle(HEAD),
              th("Domain").withStyle(HEAD),
              th("").withStyle(HEAD)
            )),
            tbody().withId("tbody-creds").with(
              each(credentials, c -> tr(
                td(c.user()).withStyle(CELL),
                td(
                  span(c.displayName() != null ? c.displayName() : "").withClass("dn-text"),
                  input().withType("text").withClass("dn-input")
                    .withValue(c.displayName() != null ? c.displayName() : "")
                    .withStyle("display:none")
                ).withStyle(CELL),
                td(c.domain() != null ? c.domain() : "—").withStyle(CELL),
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
                          input().withType("hidden").withName("userId").withValue(c.userId()),
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
                      .with(
                        input().withType("hidden").withName("userId").withValue(c.userId()),
                        button("Delete").withType("submit").withStyle(BTN_DEL)
                      )
                  )
                ).withStyle(CELL)
              ))
            )
          )
    );
  }

  private static final String CSS =
    "*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }" +
    "body { font-family: Arial, Helvetica, sans-serif; background: #f5f5f5; padding: 32px; }" +
    "h2 { font-size: 1rem; color: #1a1a2e; margin-bottom: 16px; }" +
    ".card { background: #fff; border-radius: 12px; border: 0.5px solid #ddd;" +
    "        box-shadow: 0 2px 6px rgba(0,0,0,0.06); padding: 28px; margin-bottom: 28px;" +
    "        max-width: 1024px; margin-left: auto; margin-right: auto; }" +
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
    ".tab-bar { display:flex; gap:8px; margin-bottom:24px; }" +
    ".tab-btn { padding:8px 24px; border-radius:20px; font-size:0.88rem; font-weight:600;" +
    "  cursor:pointer; border:1px solid #4a6cf7; background:transparent; color:#4a6cf7; }" +
    ".tab-btn.active { background:#4a6cf7; color:#fff; }" +
    ".tab-btn:hover:not(.active) { background:#eef2ff; }" +
    ".search-input { display:block; width:100%; padding:8px 12px; border:1px solid #ddd;" +
    "  border-radius:8px; font-size:0.88rem; margin-bottom:16px; background:#fafafa; outline:none; }" +
    ".search-input:focus { border-color:#4a6cf7; background:#fff; }" +
    ".dn-input { padding:4px 8px; border:1px solid #4a6cf7; border-radius:6px;" +
    "  font-size:0.88rem; width:200px; outline:none; }";

  private static final String JS =
    // tab switching — hash-based so refresh lands on same tab
    "function switchTab(name){" +
    "  document.querySelectorAll('.tab-content').forEach(function(el){" +
    "    el.style.display=el.id==='tab-'+name?'':'none';" +
    "  });" +
    "  document.querySelectorAll('.tab-btn').forEach(function(b){" +
    "    b.classList.toggle('active',b.dataset.tab===name);" +
    "  });" +
    "  history.replaceState(null,'',location.pathname+location.search+'#'+name);" +
    "}" +
    // activate tab from URL hash on load
    "(function(){var h=location.hash.slice(1);if(h==='credentials')switchTab('credentials');})();" +
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
