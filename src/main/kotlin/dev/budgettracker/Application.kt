package dev.budgettracker

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private val pages = linkedMapOf(
    "dashboard" to "Dashboard",
    "financial" to "Financial Overview",
    "budget" to "Budgets",
    "expenses" to "Expenses",
    "bills" to "Recurring Bills",
    "debts" to "Debts",
    "savings" to "Savings Goals",
    "remittances" to "Remittances",
    "contributions" to "Government Contributions",
    "sss" to "SSS",
    "philhealth" to "PhilHealth",
    "pagibig" to "Pag-IBIG",
    "mp2" to "MP2",
    "reports" to "Reports",
    "reminders" to "Reminders",
)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
        }
        get("/api/v1/meta") {
            call.respondText(
                """{"name":"kotlin-budget-tracker","stage":"bootstrap","persistence":false,"currencies":["AED","PHP"]}""",
                ContentType.Application.Json,
            )
        }
        get("/") { call.respondText(renderPage("dashboard"), ContentType.Text.Html) }
        get("/{section}") {
            val section = call.parameters["section"]
            if (section == null || section !in pages) {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            } else {
                call.respondText(renderPage(section), ContentType.Text.Html)
            }
        }
    }
}

private fun renderPage(section: String): String {
    val title = pages.getValue(section)
    val links = pages.entries.joinToString("") { (slug, label) ->
        val current = if (slug == section) " aria-current=\"page\"" else ""
        "<a href=\"/" + slug + "\"" + current + ">" + label + "</a>"
    }
    return """
        <!doctype html><html lang="en"><head>
        <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>%TITLE% - Kotlin Budget Tracker</title>
        <style>
        :root{font:16px system-ui;color-scheme:light dark}
        *{box-sizing:border-box}body{margin:0;background:#fcf8fb;color:#2e2830}
        .app{display:grid;grid-template-columns:250px 1fr;min-height:100vh}
        aside{padding:26px 18px;background:#fff;border-right:1px solid #efdae4}
        .brand{font-size:20px;font-weight:750;margin:0 0 20px;color:#9f365f}
        nav{display:grid;gap:3px}nav a{display:block;border-radius:7px;padding:9px 11px;text-decoration:none;color:#63505d;font-size:14px}
        nav a[aria-current=page],nav a:hover{background:#fde6f0;color:#8c2853}
        main{padding:clamp(20px,5vw,58px);max-width:1150px}h1{font-size:clamp(26px,3vw,38px);letter-spacing:-.04em}
        h2{font-size:24px}.sub{color:#876a79}
        .hero{background:#fff0f6;border:1px solid #edd9e4;border-radius:12px;padding:30px;margin:34px 0}
        .cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}
        .card{border:1px solid #edd9e4;background:white;padding:21px;border-radius:9px}
        .card p{color:#776675;line-height:1.55}.tag{font-size:12px;color:#9f365f;font-weight:700}
        @media(max-width:850px){.app{display:block}aside{border-right:0;border-bottom:1px solid #efdae4}nav{grid-template-columns:repeat(auto-fit,minmax(135px,1fr))}.cards{grid-template-columns:1fr}}
        @media(prefers-color-scheme:dark){body{background:#18141a;color:#f7eaf1}aside,.card{background:#251d25;border-color:#473441}.hero{background:#33222f;border-color:#473441}nav a{color:#cdb4c4}nav a[aria-current=page],nav a:hover{background:#4a2c3d;color:#ffc1d8}.card p,.sub{color:#ccb4c2}}
        </style></head><body>
        <div class="app"><aside><p class="brand">✿ Budget Tracker<br><small>Kotlin edition</small></p><nav aria-label="Workspace">%NAV%</nav></aside>
        <main><p class="tag">FINANCIAL WORKSPACE</p><h1>%TITLE%</h1><p class="sub">Foundation preview • no account connected</p>
        <section class="hero"><h2>AED and PHP stay separate.</h2><p>Kotlin is replacing the original Next.js runtime while preserving its financial accounting rules. This is a navigation scaffold, not a connected financial workspace.</p></section>
        <section class="cards">
        <div class="card"><p class="tag">AED</p><h2>UAE finances</h2><p>Budgets, expenses, bills, debts, savings and remittance outflows.</p></div>
        <div class="card"><p class="tag">PHP</p><h2>Philippine finances</h2><p>SSS, PhilHealth, Pag-IBIG, MP2 and remittance receipts.</p></div>
        <div class="card"><p class="tag">NOT YET CONNECTED</p><h2>Persistence</h2><p>Authentication, PostgreSQL and transaction-safe editing are planned for the next phase.</p></div>
        </section><p class="sub">No fabricated financial balances or sample user records are presented.</p>
        </main></div></body></html>
    """.trimIndent().replace("%TITLE%", title).replace("%NAV%", links)
}
