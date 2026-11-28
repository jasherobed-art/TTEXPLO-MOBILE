package com.ttexplo.mobile.net

import android.content.Context
import com.ttexplo.mobile.TTExploApp
import com.ttexplo.mobile.domain.download.DownloadData
import com.ttexplo.mobile.domain.download.parseEdits
import com.ttexplo.mobile.domain.scanner.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Servidor HTTP local (solo WiFi) para descargar la información de la terminal.
 *
 * - Exige PIN para entrar.
 * - Atiende VARIOS clientes a la vez (pool de hilos), útil cuando se conectan
 *   dos o más PC/terminales simultáneamente.
 * - Solo lectura: no permite editar ni borrar.
 * - Vive mientras la pantalla del Centro de descarga está abierta.
 */
class DownloadServer(
    context: Context,
    private val port: Int = 8080
) {
    private val appContext = context.applicationContext
    private val data = DownloadData(
        (context.applicationContext as TTExploApp).database,
        context.applicationContext
    )
    private val indexHtml by lazy { assetText("index.html") }
    private val appCss by lazy { assetText("app.css") }
    private val appJs by lazy { assetText("app.js") }

    private fun assetText(name: String): String =
        appContext.assets.open("web/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val sessionToken = UUID.randomUUID().toString()
    private val seenClients = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var pool: java.util.concurrent.ExecutorService? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null
    @Volatile private var presenceThread: Thread? = null

    @Volatile var running: Boolean = false
        private set

    @Volatile var pin: String = newPin()
        private set

    /** Clientes conectados en este momento (para el foco de estado en pantalla). */
    val clients = MutableStateFlow(0)

    /** Última vez que una PC hizo una petición (epoch ms). */
    val lastClientAt = MutableStateFlow(0L)

    fun newPin(): String = Random.nextInt(100, 1000).toString()

    fun regeneratePin() {
        pin = newPin()
    }

    fun start() {
        if (running) return
        val socket = ServerSocket(port)
        serverSocket = socket
        val executor = Executors.newFixedThreadPool(8)
        pool = executor
        running = true
        acceptThread = Thread {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    null
                } ?: break
                try {
                    executor.execute { handle(client) }
                } catch (_: Exception) {
                    // El servidor se apagó entre accept y execute: se descarta el cliente.
                    runCatching { client.close() }
                }
            }
        }.also { it.isDaemon = true; it.start() }
        presenceThread = Thread {
            while (running) {
                try {
                    Thread.sleep(PRESENCE_POLL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                pruneClients()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        runCatching { pool?.shutdownNow() }
        pool = null
        runCatching { presenceThread?.interrupt() }
        presenceThread = null
        seenClients.clear()
        clients.value = 0
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 10_000
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] =
                        line.substring(idx + 1).trim()
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength in 1..MAX_BODY) {
                    val chars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(chars, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(chars, 0, read)
                } else ""

                val path = rawPath.substringBefore('?')
                val query = if (rawPath.contains('?')) rawPath.substringAfter('?') else ""
                val queryParams = parseQuery(query)
                val cookie = headers["cookie"].orEmpty()

                val authorized = cookie.contains("ttex_session=$sessionToken")
                if (authorized) {
                    markClient(s.inetAddress?.hostAddress ?: "local")
                    lastClientAt.value = System.currentTimeMillis()
                }

                when {
                    path == "/login" && method == "POST" -> {
                        val given = parseQuery(body)["pin"]
                        if (given == pin) {
                            writeResponse(
                                s, 302, "text/plain; charset=utf-8", "OK",
                                listOf(
                                    "Location: /",
                                    "Set-Cookie: ttex_session=$sessionToken; Path=/; HttpOnly; SameSite=Lax"
                                )
                            )
                        } else {
                            writeResponse(s, 200, "text/html; charset=utf-8",
                                loginPage("PIN incorrecto. Intenta de nuevo."))
                        }
                    }
                    path == "/login" || !authorized -> {
                        if (path.startsWith("/api/") || path.startsWith("/export/")) {
                            writeResponse(s, 401, "application/json; charset=utf-8",
                                "{\"error\":\"unauthorized\"}")
                        } else {
                            writeResponse(s, 200, "text/html; charset=utf-8", loginPage(null))
                        }
                    }
                    path == "/api/ping" -> {
                        writeResponse(s, 200, "application/json; charset=utf-8", "{\"ok\":true}")
                    }
                    path == "/api/summary" -> {
                        val json = runBlocking { data.summaryJson() }
                        writeResponse(s, 200, "application/json; charset=utf-8", json)
                    }
                    path == "/export/vales.csv" -> export(s, "vales.csv", "text/csv; charset=utf-8") {
                        runBlocking { data.valesCsv() }
                    }
                    path == "/export/inventario.csv" -> export(s, "inventario.csv", "text/csv; charset=utf-8") {
                        runBlocking { data.inventoryCsv() }
                    }
                    path == "/export/catalogo.csv" -> export(s, "catalogo.csv", "text/csv; charset=utf-8") {
                        runBlocking { data.catalogCsv() }
                    }
                    path == "/export/catalogo-alto.csv" -> export(s, "catalogo_alto_agente.csv", "text/csv; charset=utf-8") {
                        runBlocking { data.catalogCsv(setOf("ALTO_EXPLOSIVO", "AGENTE")) }
                    }
                    path == "/export/catalogo-accesorios.csv" -> export(s, "catalogo_accesorios.csv", "text/csv; charset=utf-8") {
                        runBlocking { data.catalogCsv(setOf("ACCESORIO")) }
                    }
                    path == "/export/vales.txt" -> {
                        val params = if (method == "POST") parseQuery(body) else queryParams
                        val keys = params["keys"]?.split(',')
                            ?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                        val edits = params["edits"]?.let {
                            runCatching { parseEdits(it) }.getOrNull()
                        }
                        // Nombre con fecha y hora: varios consolidados del mismo día no se pisan.
                        val stamp = DateTimeUtils.currentTimestamp().take(16)
                            .replace(" ", "_").replace(":", "")
                        export(s, "vales-consolidado-$stamp.txt", "text/plain; charset=utf-8") {
                            runBlocking { data.valesTxt(keys, edits) }
                        }
                    }
                    path == "/export/todo.json" -> export(s, "ttexplo_datos.json", "application/json; charset=utf-8") {
                        runBlocking { data.allJson() }
                    }
                    path == "/api/catalogo/producto" && method == "POST" -> {
                        val code = runBlocking { data.saveProductJson(body) }
                        when (code) {
                            0 -> writeResponse(s, 200, "application/json; charset=utf-8", "{\"ok\":true}")
                            2 -> writeResponse(s, 409, "application/json; charset=utf-8",
                                "{\"error\":\"ya_existe\"}")
                            else -> writeResponse(s, 404, "application/json; charset=utf-8",
                                "{\"error\":\"no_encontrado\"}")
                        }
                    }
                    path == "/api/catalogo" && method == "GET" -> {
                        val tipos = when (queryParams["tipo"].orEmpty().uppercase()) {
                            "ALTO" -> setOf("ALTO_EXPLOSIVO", "AGENTE")
                            "ACCESORIOS" -> setOf("ACCESORIO")
                            else -> null
                        }
                        val json = runBlocking { data.catalogJson(tipos, queryParams["q"]) }
                        writeResponse(s, 200, "application/json; charset=utf-8", json)
                    }
                    path == "/api/catalogo" && method == "POST" -> {
                        when {
                            contentLength > MAX_BODY -> writeResponse(
                                s, 413, "application/json; charset=utf-8",
                                "{\"error\":\"archivo_muy_grande\"}"
                            )
                            body.isBlank() -> writeResponse(
                                s, 400, "application/json; charset=utf-8",
                                "{\"error\":\"sin_datos\"}"
                            )
                            else -> {
                                val imported = runBlocking { data.importCatalogCsv(body) }
                                if (imported <= 0) writeResponse(
                                    s, 422, "application/json; charset=utf-8",
                                    "{\"error\":\"sin_productos\"}"
                                ) else writeResponse(
                                    s, 200, "application/json; charset=utf-8",
                                    "{\"importados\":$imported}"
                                )
                            }
                        }
                    }
                    path == "/api/vale" -> {
                        val key = queryParams["key"].orEmpty()
                        val json = if (key.isBlank()) null else runBlocking { data.valeDetail(key) }
                        if (json == null) {
                            writeResponse(s, 404, "application/json; charset=utf-8",
                                "{\"error\":\"not_found\"}")
                        } else {
                            writeResponse(s, 200, "application/json; charset=utf-8", json)
                        }
                    }
                    path == "/api/vales" -> {
                        writeResponse(s, 200, "application/json; charset=utf-8", runBlocking { data.valesJson() })
                    }
                    path == "/vale" -> {
                        val tipo = queryParams["tipo"].orEmpty()
                        val id = queryParams["id"]?.toLongOrNull() ?: 0L
                        val folio = queryParams["folio"]
                        val plain = runBlocking { data.ticketFor(tipo, id, folio) }
                        if (plain == null) {
                            writeResponse(s, 404, "text/plain; charset=utf-8", "Vale no encontrado")
                        } else {
                            writeResponse(s, 200, "text/html; charset=utf-8", ticketPage(tipo, plain))
                        }
                    }
                    path == "/" || path == "/index.html" ->
                        writeResponse(s, 200, "text/html; charset=utf-8", indexHtml, SEC_HEADERS)
                    path == "/app.css" ->
                        writeResponse(s, 200, "text/css; charset=utf-8", appCss, SEC_HEADERS)
                    path == "/app.js" ->
                        writeResponse(s, 200, "text/javascript; charset=utf-8", appJs, SEC_HEADERS)
                    else -> writeResponse(s, 404, "text/plain; charset=utf-8", "No encontrado")
                }
            }
        } catch (_: Exception) {
            // Cliente desconectado o petición inválida: se ignora.
        }
    }

    private fun export(socket: Socket, fileName: String, contentType: String, build: () -> String) {
        val body = build()
        writeResponse(
            socket, 200, contentType, body,
            listOf("Content-Disposition: attachment; filename=\"$fileName\"")
        )
    }

    private fun writeResponse(
        socket: Socket,
        status: Int,
        contentType: String,
        body: String,
        extraHeaders: List<String> = emptyList()
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 302 -> "Found"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "OK"
        }
        val out: OutputStream = socket.getOutputStream()
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status $reason\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: ${bytes.size}\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("Connection: close\r\n")
        for (h in extraHeaders) sb.append(h).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun markClient(ip: String) {
        seenClients[ip] = System.currentTimeMillis()
        pruneClients()
    }

    private fun pruneClients() {
        val cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS
        seenClients.entries.removeIf { it.value < cutoff }
        clients.value = seenClients.size
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null
            else URLDecoder.decode(it.substring(0, idx), "UTF-8") to
                URLDecoder.decode(it.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    companion object {
        private const val PRESENCE_TTL_MS = 45_000L
        private const val PRESENCE_POLL_MS = 15_000L
        private const val MAX_BODY = 1_000_000

        private val SEC_HEADERS = listOf(
            "X-Content-Type-Options: nosniff",
            "X-Frame-Options: DENY",
            "Referrer-Policy: no-referrer",
            "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'"
        )

        fun deviceIp(): String {
            return try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
                    ?: "0.0.0.0"
            } catch (_: Exception) {
                "0.0.0.0"
            }
        }
    }

    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun ticketPage(tipo: String, plain: String): String {
        val lines = plain.lines().filter { it.isNotBlank() }
        val body = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val isTotal = line.contains("TOTAL DE MATERIALES", ignoreCase = true)
            if (isTotal) {
                val next = lines.getOrNull(i + 1)
                val hasBreakdown = next?.contains("CAJAS", ignoreCase = true) == true
                body.append("<div class=\"total\">").append(esc(line.trim()))
                if (hasBreakdown && next != null) body.append("<br>").append(esc(next.trim()))
                body.append("</div>")
                i += if (hasBreakdown) 2 else 1
            } else {
                body.append("<div>").append(esc(line)).append("</div>")
                i++
            }
        }
        return """
<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Vale ${esc(tipo)}</title>
<style>
body{margin:0;background:#0b0b0b;color:#ececec;font-family:-apple-system,"Segoe UI",Roboto,sans-serif}
header{display:flex;align-items:center;gap:16px;padding:18px 24px;border-bottom:1px solid #383838}
header h1{font-size:18px;margin:0;color:#ffb300}
header a{color:#29b6f6;text-decoration:none;font-size:13px}
main{padding:24px;display:flex;justify-content:center}
.ticket{background:#fff;color:#000;font-family:ui-monospace,Menlo,Consolas,monospace;
font-size:12px;line-height:1.45;padding:18px 16px;width:340px;border-radius:4px;box-shadow:0 8px 30px rgba(0,0,0,.5)}
.ticket .total{background:#000;color:#fff;padding:4px 6px;margin:6px 0;font-weight:bold}
</style></head><body>
<header><a href="/">← Volver</a><h1>Vale ${esc(tipo)}</h1></header>
<main><div class="ticket">$body</div></main></body></html>
""".trimIndent()
    }

    private fun loginPage(error: String?): String = """
<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>TTEXPLO · Acceso</title>
<style>
body{margin:0;background:#0b0b0b;color:#ececec;font-family:-apple-system,"Segoe UI",Roboto,sans-serif;
display:flex;align-items:center;justify-content:center;min-height:100vh}
.card{background:#1e1e1e;border:1px solid #383838;border-radius:14px;padding:32px;width:340px;text-align:center}
h1{color:#ffb300;font-size:20px;margin:0 0 6px}p{color:#9a9a9a;font-size:13px;margin:0 0 18px}
input{width:100%;box-sizing:border-box;background:#121212;border:1px solid #555;border-radius:8px;
padding:12px;color:#fff;font-size:20px;text-align:center;letter-spacing:6px;margin-bottom:12px;outline:none}
input:focus{border-color:#ffb300}
button{width:100%;background:#ffb300;color:#111;border:0;border-radius:8px;padding:12px;
font-size:14px;font-weight:bold;cursor:pointer}
.err{color:#e57373;font-size:12px;margin-bottom:10px}
</style></head><body><div class="card">
<h1>T&amp;T EXPLO</h1><p>Ingresa el PIN de 3 dígitos que muestra la terminal</p>
${error?.let { "<div class=\"err\">$it</div>" } ?: ""}
<form method="post" action="/login">
<input name="pin" inputmode="numeric" autocomplete="one-time-code" maxlength="3" autofocus placeholder="PIN">
<button type="submit">ENTRAR</button>
</form></div></body></html>
""".trimIndent()

    private fun dashboardPage(): String = """
<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>TTEXPLO · Descarga</title>
<style>
:root{--bg:#0b0b0b;--card:#1e1e1e;--line:#383838;--yellow:#ffb300;--text:#ececec;--muted:#9a9a9a;--green:#81c784}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);
font-family:-apple-system,"Segoe UI",Roboto,sans-serif}
header{display:flex;align-items:center;gap:12px;padding:20px 28px;border-bottom:1px solid var(--line)}
header h1{font-size:20px;margin:0;color:var(--yellow)}header span{color:var(--muted);font-size:13px}
main{padding:24px 28px;max-width:1100px;margin:0 auto}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:24px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}
.card .n{font-size:30px;font-weight:800;color:var(--yellow);font-variant-numeric:tabular-nums}
.card .l{font-size:12px;color:var(--muted);margin-top:4px;text-transform:uppercase;letter-spacing:.5px}
h2{font-size:15px;color:var(--text);margin:0 0 12px}
.exports{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}
a.btn{display:flex;align-items:center;gap:10px;background:var(--card);border:1px solid var(--line);
border-radius:10px;padding:14px 16px;color:var(--text);text-decoration:none;font-size:14px;font-weight:600}
a.btn:hover{border-color:var(--yellow);color:var(--yellow)}
a.btn .ico{color:var(--green);font-size:18px}
.vales{display:flex;flex-direction:column;gap:8px}
a.vale{display:grid;grid-template-columns:90px 110px 1fr 150px;gap:10px;align-items:center;
background:var(--card);border:1px solid var(--line);border-radius:8px;padding:10px 12px;
color:var(--text);text-decoration:none;font-size:13px}
a.vale:hover{border-color:var(--yellow);color:var(--yellow)}
a.vale .t{color:var(--green);font-weight:bold;font-size:11px}
a.vale .f{font-weight:bold}
a.vale .r{color:var(--muted)}
a.vale .d{color:var(--muted);font-size:11px;text-align:right}
footer{color:var(--muted);font-size:12px;padding:24px 28px;text-align:center}
</style></head><body>
<header><h1>T&amp;T EXPLO MOBILE</h1><span>Centro de descarga · solo lectura</span></header>
<main>
<h2>Resumen de la terminal</h2>
<div class="grid" id="summary"></div>
<h2>Descargar información</h2>
<div class="exports">
<a class="btn" href="/export/vales.csv"><span class="ico">↓</span> Vales (CSV)</a>
<a class="btn" href="/export/vales.txt"><span class="ico">↓</span> Vales (.txt para T&amp;T Explo)</a>
<a class="btn" href="/export/inventario.csv"><span class="ico">↓</span> Inventario (CSV)</a>
<a class="btn" href="/export/catalogo.csv"><span class="ico">↓</span> Catálogo (CSV)</a>
<a class="btn" href="/export/todo.json"><span class="ico">↓</span> Paquete completo (JSON)</a>
</div>
<h2 style="margin-top:28px">Vales registrados</h2>
<div id="vales" class="vales"></div>
</main>
<footer>T&amp;T Explo Mobile · datos en tiempo real de esta terminal</footer>
<script>
const labels={totalVales:'Vales',entradas:'Entradas',despachos:'Salidas',devoluciones:'Devoluciones',
accesorios:'Accesorios',conteos:'Conteos',productos:'Productos',eventosInventario:'Eventos inventario'};
fetch('/api/summary').then(r=>r.json()).then(d=>{
 const g=document.getElementById('summary');
 for(const k of Object.keys(labels)){
   const c=document.createElement('div');c.className='card';
   c.innerHTML='<div class="n">'+(d[k]??0)+'</div><div class="l">'+labels[k]+'</div>';
   g.appendChild(c);
 }
}).catch(()=>{document.getElementById('summary').innerHTML='<div class="card">No se pudo cargar el resumen.</div>'});
fetch('/api/vales').then(r=>r.json()).then(list=>{
 const g=document.getElementById('vales');
 if(!list.length){g.innerHTML='<div class="card">Sin vales registrados.</div>';return;}
 for(const v of list){
   const a=document.createElement('a');a.className='vale';a.href=v.url;
   const t=document.createElement('span');t.className='t';t.textContent=v.tipo;
   const f=document.createElement('span');f.className='f';f.textContent=v.folio;
   const r=document.createElement('span');r.className='r';r.textContent=v.resumen;
   const d=document.createElement('span');d.className='d';d.textContent=v.fecha;
   a.append(t,f,r,d);g.appendChild(a);
 }
}).catch(()=>{document.getElementById('vales').innerHTML='<div class="card">No se pudieron cargar los vales.</div>'});
setInterval(()=>fetch('/api/ping').catch(()=>{}),15000);
</script></body></html>
""".trimIndent()
}
