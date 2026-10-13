// O aplicativo do FxBackendBench em Compose Desktop: a mesma tela de formulários grandes,
// as mesmas fases e as mesmas linhas "JX_METRIC chave=valor".
//
// Equivalências: o acordeão são cabeçalhos clicáveis com um grupo aberto por vez; a caixa de
// combinação é um campo com um DropdownMenu (fechado); o spinner é um campo numérico com botões;
// a data é um campo com a data ISO (o material do Compose Desktop não tem seletor de data); a
// tabela e a lista são LazyColumn (rolagem virtual, como as células do JavaFX). "Um quadro com a
// mudança" é o relógio de quadros do próprio Compose (withFrameNanos), duas vezes, como nas outras
// versões. Roda no JDK 17 de 64 bits: o Compose não suporta Java 8.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.lang.management.ManagementFactory
import java.time.LocalDate
import kotlin.system.exitProcess

val PANES = Integer.getInteger("bench.panes", 6)
val ROWS = Integer.getInteger("bench.rows", 10)
val TABLE = Integer.getInteger("bench.table", 10_000)
val BIG = Integer.getInteger("bench.big", 100_000)
val RECORDS = Integer.getInteger("bench.records", 50)
val TAB_SWITCHES = Integer.getInteger("bench.tabs", 30)
val SCROLLS = Integer.getInteger("bench.scrolls", 200)
val FRAMES = Integer.getInteger("bench.frames", 600)
val MEMORY = ManagementFactory.getMemoryMXBean()
@Volatile var peakHeap = 0L

class Row(i: Int) {
    val id = i
    val name = "Equipamento $i"
    val city = if (i % 3 == 0) "Fortaleza" else if (i % 3 == 1) "Recife" else "Natal"
    val status = if (i % 7 == 0) "Inativo" else "Ativo"
    val value = (i * 37) % 1000
}

fun rows(n: Int) = List(n) { Row(it) }

/** O valor de um campo, pelo tipo (o índice i % 6 decide o tipo, como nas outras versões). */
class Field(val index: Int) {
    var text by mutableStateOf(
        when (index % 6) { 0 -> "valor $index"; else -> "" })
    var choice by mutableStateOf("A")
    var checked by mutableStateOf(false)
    var number by mutableStateOf(index)
    var date by mutableStateOf(LocalDate.of(2026, 1, 1).plusDays(index.toLong()))

    fun state(): String = when (index % 6) {
        0, 5 -> text
        1 -> choice
        2 -> checked.toString()
        3 -> number.toString()
        else -> date.toString()
    }
}

fun main() {
    metric("backend_native", 0)
    Thread({
        while (true) {
            peakHeap = maxOf(peakHeap, MEMORY.heapMemoryUsage.used)
            Thread.sleep(10)
        }
    }, "bench-sampler").apply { isDaemon = true }.start()
    metric("start_uptime_ms", ManagementFactory.getRuntimeMXBean().uptime)
    val cpu0 = cpuNanos()
    val alloc0 = allocatedBytes()
    val t0 = System.nanoTime()
    val fields = List(PANES * ROWS) { Field(it) }
    metric("fields", fields.size.toLong())
    metric("nodes_created", (PANES * ROWS * 2 + PANES * 2 + 16 + TABLE).toLong())

    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        Window(onCloseRequest = ::exitApplication, title = "Large-form bench (Compose)", state = windowState) {
            var status by remember { mutableStateOf("Pronto") }
            var progress by remember { mutableStateOf(0f) }
            var tab by remember { mutableStateOf(0) }
            var open by remember { mutableStateOf(0) }
            var tableRows by remember { mutableStateOf(rows(TABLE)) }
            val tableState = rememberLazyListState()
            val events = remember { List(1000) { "Evento $it" } }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Novo") }
                    Button(onClick = {}) { Text("Salvar") }
                    var busca by remember { mutableStateOf("") }
                    TextField(busca, { busca = it }, placeholder = { Text("Buscar equipamento") }, singleLine = true)
                    Combo("Todos")
                }
                TabRow(selectedTabIndex = tab) {
                    listOf("Cadastro", "Registros", "Eventos").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            for (p in 0 until PANES) {
                                Text("Grupo ${p + 1}", Modifier.fillMaxWidth().clickable { open = p }.padding(8.dp),
                                    style = MaterialTheme.typography.subtitle1)
                                if (open == p) {
                                    Column(Modifier.padding(8.dp)) {
                                        for (r in 0 until ROWS) {
                                            val f = fields[p * ROWS + r]
                                            Row(Modifier.padding(vertical = 3.dp)) {
                                                Text("Parâmetro ${p * ROWS + r}", Modifier.width(140.dp))
                                                FieldView(f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> LazyColumn(state = tableState) {
                            items(tableRows) { row ->
                                Row(Modifier.fillMaxWidth().padding(2.dp)) {
                                    for (cell in listOf(row.id.toString(), row.name, row.city, row.status, row.value.toString())) {
                                        Text(cell, Modifier.width(150.dp))
                                    }
                                }
                            }
                        }
                        else -> LazyColumn { items(events) { Text(it, Modifier.padding(2.dp)) } }
                    }
                }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(status)
                    LinearProgressIndicator(progress, Modifier.width(200.dp))
                }
            }

            LaunchedEffect(Unit) {
                frames(2)
                metric("build_show_ns", System.nanoTime() - t0)
                metric("build_to_frame_ns", System.nanoTime() - t0)
                metric("build_cpu_ns", cpuNanos() - cpu0)
                metric("build_allocated_bytes", allocatedBytes() - alloc0)
                metric("first_frame_uptime_ms", ManagementFactory.getRuntimeMXBean().uptime)
                metric("first_frame_process_cpu_ns", cpuNanos())

                phase("records") {
                    var work = 0L
                    for (i in 0 until RECORDS) {
                        val w = System.nanoTime()
                        for ((k, f) in fields.withIndex()) {
                            val v = i * 31 + k
                            when (k % 6) {
                                0 -> f.text = "registro $i campo $k"
                                1 -> f.choice = ('A' + v % 10).toString()
                                2 -> f.checked = v % 2 == 0
                                3 -> f.number = v % 1000
                                4 -> f.date = LocalDate.of(2026, 1, 1).plusDays((v % 365).toLong())
                                else -> f.text = "registro $i campo $k"
                            }
                        }
                        status = "Registro $i"
                        work += System.nanoTime() - w
                        frames(2)
                    }
                    metric("records_work_ns", work)
                }
                metric("records_state_hash", (fields.joinToString("") { it.state() + "|" } + status).hashCode().toLong() and 0xffffffffL)

                phase("tabs") {
                    for (i in 0 until TAB_SWITCHES) {
                        tab = (i + 1) % 3
                        status = "Aba $i"
                        frames(2)
                    }
                }
                tab = 1
                frames(2)
                phase("scroll") {
                    val stride = maxOf(1, TABLE / SCROLLS)
                    for (i in 0 until SCROLLS) {
                        tableState.scrollToItem(i * stride)
                        status = "Linha ${i * stride}"
                        frames(2)
                    }
                }
                tab = 0
                frames(2)
                val sustainedCpu = cpuNanos()
                val sustainedAlloc = allocatedBytes()
                val ts = System.nanoTime()
                val stamps = LongArray(FRAMES)
                for (i in 0 until FRAMES) {
                    status = "Quadro $i"
                    progress = (i % 100) / 100f
                    fields[0].text = "quadro $i"
                    stamps[i] = withFrameNanos { System.nanoTime() }
                }
                val elapsed = System.nanoTime() - ts
                metric("sustained_ns", elapsed)
                metric("sustained_cpu_ns", cpuNanos() - sustainedCpu)
                metric("sustained_allocated_bytes", allocatedBytes() - sustainedAlloc)
                frameStats(stamps, elapsed)

                val bigCpu = cpuNanos()
                val bigAlloc = allocatedBytes()
                val tb = System.nanoTime()
                tab = 1
                tableRows = rows(BIG)
                frames(1)
                tableState.scrollToItem(BIG / 2)
                metric("big_items_ns", System.nanoTime() - tb)
                frames(2)
                metric("big_to_frame_ns", System.nanoTime() - tb)
                metric("big_cpu_ns", cpuNanos() - bigCpu)
                metric("big_allocated_bytes", allocatedBytes() - bigAlloc)
                finish()
            }
        }
    }
}

@Composable
fun Combo(value: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded, { expanded = false }) {
            for (c in listOf("Todos", "Ativos", "Inativos")) DropdownMenuItem(onClick = { expanded = false }) { Text(c) }
        }
    }
}

@Composable
fun FieldView(f: Field) {
    when (f.index % 6) {
        0 -> TextField(f.text, { f.text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        1 -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(f.choice) }
                DropdownMenu(expanded, { expanded = false }) {
                    for (c in 'A'..'J') DropdownMenuItem(onClick = { f.choice = c.toString(); expanded = false }) { Text(c.toString()) }
                }
            }
        }
        2 -> Row { Checkbox(f.checked, { f.checked = it }); Text("Habilitado") }
        3 -> Row {
            TextField(f.number.toString(), { s -> s.toIntOrNull()?.let { f.number = it } }, singleLine = true, modifier = Modifier.width(120.dp))
            Button(onClick = { f.number = minOf(1000, f.number + 1) }) { Text("+") }
            Button(onClick = { f.number = maxOf(0, f.number - 1) }) { Text("-") }
        }
        4 -> TextField(f.date.toString(), {}, singleLine = true, modifier = Modifier.fillMaxWidth())
        else -> TextField(f.text, { f.text = it }, placeholder = { Text("opcional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

suspend fun frames(n: Int) {
    repeat(n) { withFrameNanos { } }
}

suspend fun phase(name: String, body: suspend () -> Unit) {
    val cpu = cpuNanos()
    val alloc = allocatedBytes()
    val t0 = System.nanoTime()
    body()
    metric("${name}_ns", System.nanoTime() - t0)
    metric("${name}_cpu_ns", cpuNanos() - cpu)
    metric("${name}_allocated_bytes", allocatedBytes() - alloc)
}

fun finish() {
    var gcCount = 0L
    var gcMillis = 0L
    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
        gcCount += maxOf(0, gc.collectionCount)
        gcMillis += maxOf(0, gc.collectionTime)
    }
    metric("gc_count", gcCount)
    metric("gc_time_ms", gcMillis)
    metric("heap_peak_bytes", peakHeap)
    metric("nonheap_bytes", MEMORY.nonHeapMemoryUsage.used)
    metric("allocated_total_bytes", allocatedBytes())
    metric("process_cpu_total_ns", cpuNanos())
    metric("thread_cpu_total_ns", threadCpuNanos())
    metric("classes_loaded", ManagementFactory.getClassLoadingMXBean().totalLoadedClassCount)
    metric("thread_count", ManagementFactory.getThreadMXBean().threadCount.toLong())
    System.gc()
    System.gc()
    metric("heap_live_after_gc_bytes", MEMORY.heapMemoryUsage.used)
    metric("done_uptime_ms", ManagementFactory.getRuntimeMXBean().uptime)
    System.out.flush()
    exitProcess(0)
}

fun metric(name: String, value: Long) = println("JX_METRIC $name=$value")

fun cpuNanos(): Long = (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).processCpuTime

fun threadCpuNanos(): Long {
    val t = ManagementFactory.getThreadMXBean()
    return t.allThreadIds.sumOf { maxOf(0L, t.getThreadCpuTime(it)) }
}

fun allocatedBytes(): Long {
    val t = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    return t.getThreadAllocatedBytes(t.allThreadIds).sumOf { maxOf(0L, it) }
}

fun frameStats(frames: LongArray, elapsed: Long) {
    val intervals = LongArray(frames.size - 1) { frames[it + 1] - frames[it] }
    intervals.sort()
    metric("sustained_frames", frames.size.toLong())
    metric("sustained_fps_x100", frames.size * 100_000_000_000L / maxOf(1L, elapsed))
    metric("frame_p50_us", intervals[intervals.size / 2] / 1000)
    metric("frame_p95_us", intervals[Math.ceil(intervals.size * 0.95).toInt() - 1] / 1000)
    metric("frame_p99_us", intervals[Math.ceil(intervals.size * 0.99).toInt() - 1] / 1000)
    metric("frame_max_us", intervals.last() / 1000)
    metric("jank_frames", intervals.count { it > 25_000_000L }.toLong())
}
