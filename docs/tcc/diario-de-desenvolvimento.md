# Diário de desenvolvimento do JXParallel

Registro cronológico das estratégias, decisões e métricas do projeto, mantido como fonte para
o TCC em Engenharia de Computação (IFCE). Cada entrada diz o que foi tentado, por quê, o que as
medições mostraram e o que mudou depois. Números sempre com data, ambiente e link para os dados
brutos em `docs/`.

**Objetivo do projeto** (definido pelo autor em 2026-09-24): reproduzir o que o JavaFX oferece,
fazer melhor e ir além, com foco em desempenho para aplicações grandes. Público-alvo: Java 8 até
a versão mais recente, em 32 e 64 bits.

## Metodologia de medição (estado atual)

- Cada implementação roda em uma JVM nova por execução, alternando JavaFX e JXParallel para
  que variações da máquina afetem os dois lados.
- 5 execuções por lado; o relatório usa a mediana e mostra a faixa entre execuções.
- Memória do processo (working set, bytes privados, handles) amostrada fora da JVM a cada
  10 ms com `Get-Process`. Heap, non-heap e memória direta amostrados dentro da JVM a cada 10 ms.
- CPU de processo via `OperatingSystemMXBean.getProcessCpuTime` (granularidade de 15.6 ms no
  Windows). Alocação via `ThreadMXBean.getThreadAllocatedBytes` das threads vivas.
- Os dois lados executam exatamente os mesmos cenários e as mudanças precisam chegar à tela
  (ver entrada 2026-09-24, "Validade do teste de UI").
- A carga de outros processos é registrada antes e depois de cada bateria. Baterias com a
  máquina ocupada são descartadas ou marcadas.

Máquina de referência: Windows 11, Intel Core i7-1255U (12 threads), Intel Iris Xe, tela a
cerca de 102 Hz. JDK 17.0.12 x64 e OpenJFX 21.0.2, salvo indicação.

---

## 2026-09-22: primeira versão (desenvolvida com o Google Antigravity)

**Estratégia.** Biblioteca em módulos Maven: `jxparallel-core` (pool de workers adaptativo com
fila limitada, prioridades, cancelamento, métricas), ponte com o JavaFX (`jxparallel-javafx`,
`jxparallel-fxml` com cache de FXML) e um modelo de UI próprio, independente do JavaFX
(`jxparallel-ui`), renderizado primeiro com Java2D.
Commits `1765c72` a `4652c57`.

**Sequência de motores gráficos no mesmo dia.**
1. Java2D (AWT), commit `1765c72`.
2. Skia via Skija, desenhando num bitmap e copiando para um `BufferedImage` do AWT (`d331296`).
3. Backend OpenGL opcional com LWJGL, primitivas cruas sem Skia (`1ba1d5e`).
4. Backend Vulkan com LWJGL (`7f601d7`), promovido a motor principal (`8453c0e`).

**Primeiras métricas** (JDK 21 x64, [ui-performance-2026-09-22.md](../ui-performance-2026-09-22.md)):

| Métrica | JavaFX | JXParallel (Skia) |
|---|---:|---:|
| Início até o primeiro quadro | 312 ms | 397 ms (+27%) |
| CPU de processo | 750 ms | 469 ms |
| Delta de heap | 7.2 MB | 10.2 MB |

Java 8 x86 ([metrics-java8-x86-report.md](../metrics-java8-x86-report.md)): início +11.3%,
clique até o resultado +7.0%, delta de heap -45.8%, CPU +15.2% para o JXParallel.

**Teste de estresse de UI** (`docs/ui-stress-load-report.md`, removido do repositório em 2026-09-25;
recuperável com `git show 4652c57:docs/ui-stress-load-report.md`): o relatório
indicava o JXParallel 40% mais rápido no total e 74.6% mais rápido para atualizar um label.
Esses números foram invalidados em 2026-09-24 (ver abaixo).

**Problema encontrado.** O Vulkan derrubava a JVM com `EXCEPTION_ACCESS_VIOLATION` no driver da
Intel (`igvk64.dll`). Causas levantadas: dependência de subpass sem `srcAccessMask` e swapchain
não recriado em `VK_ERROR_OUT_OF_DATE_KHR`.

---

## 2026-09-24: abandono do Vulkan; Skia sobre GLFW/OpenGL

**Decisão.** Abandonar o Vulkan e usar Skia (Skija) desenhando direto no framebuffer de uma
janela GLFW com contexto OpenGL, via `DirectContext` da GPU. Eliminou a cópia pixel a pixel
para `BufferedImage` e a dependência do AWT na janela. Commit `116b1fe`.

**Motivação.** O Vulkan exigia muito código (910 linhas só na janela) e ainda não desenhava
texto; o crash na Intel bloqueava o uso real. O caminho Skia + OpenGL já existia em partes.

**Resultado.** A janela abre e desenha texto e controles sem crash. Restrição descoberta: o Skija
não publica bibliotecas nativas de 32 bits, então a janela só roda em JVM 64-bit.

---

## 2026-09-24: validade do teste de UI

**Problema metodológico.** No `NativeStressRunner`, `JXLabel.setText()` seguido de
`window.requestRender()` não mudava nada na tela: a árvore nativa é um instantâneo criado em
`setContent(content.render())`. O teste media só o custo de um setter, enquanto o lado JavaFX
pagava invalidação de CSS e layout. Isso favorecia o JXParallel.

**Correção.** Cada atualização passou a chamar `setContent(content.render())`, que é o custo
real para a mudança chegar à tela. Foi adicionada uma fase sustentada: 600 quadros, cada um
atualizando todos os componentes, com ritmo definido pelo vsync, medindo intervalo entre quadros
(p50, p95, p99, pior quadro), quadros acima de 25 ms, CPU e alocação. Commit `a955d1c`.

**Métricas após a correção** (mediana de 5, [ui-stress-comparison-2026-09-24.md](../ui-stress-comparison-2026-09-24.md)):

| Métrica | JavaFX | JXParallel | Diferença |
|---|---:|---:|---:|
| Pico de RAM (working set) | 248.6 MB | 159.8 MB | -36% |
| Heap vivo após GC | 10.6 MB | 2.2 MB | -80% |
| CPU total do processo | 10.0 s | 3.1 s | -69% |
| CPU por quadro | 10.6 ms | 3.0 ms | -72% |
| Pior quadro | 53.2 ms | 21.4 ms | -60% |
| Atualizar label (500x) | 11.6 ms | 30.0 ms | **+160%** |

**Leitura.** Com o teste corrigido, o label passou de "74.6% mais rápido" para "160% mais lento":
reconstruir a árvore inteira a cada mudança custa mais que a invalidação pontual do JavaFX.
Parte da vantagem em memória e CPU vem de o JXParallel desenhar menos (sem CSS, sem skins).

**Reexecução em 2026-09-25** com a máquina ociosa: a apresentação de quadros ficou limitada
(cerca de 30 e 25 FPS nos dois lados), invalidando as métricas de quadro. Memória (-34%), rajada
(-77%) e CPU por quadro (-47%) confirmaram a direção.

*Correção de diagnóstico.* Atribuí a limitação à sessão bloqueada porque o processo `LogonUI`
estava ativo. Depois se verificou que o `LogonUI` pertencia à sessão desconectada de outro usuário
da mesma máquina, e que a área de trabalho estava visível. A causa mais provável foi o monitor
desligado por inatividade enquanto o autor acompanhava remotamente. Lição: verificar se os
quadros estão sendo apresentados (FPS próximo da taxa do monitor) antes de aceitar uma bateria,
em vez de inferir o estado da tela por processos.

---

## 2026-09-24: suporte a 32 e 64 bits, Java 8 até a versão atual

**Levantamento** (Maven Central, LWJGL 3.3.3): há bibliotecas nativas `windows-x86` para
`lwjgl`, `glfw`, `opengl`, `nanovg`, `stb`, `freetype`, `harfbuzz` e `yoga`. O Skija não tem
(`skija-windows-x86` retorna 404).

**Restrição da plataforma.** O port Windows x86 foi descontinuado no JDK 21 (JEP 449) e removido
no 24 (JEP 479). *Correção em 2026-09-25:* a primeira execução da CI mostrou que nenhuma das
distribuições usadas (Temurin, Zulu) publica JDK 21 de 32 bits para Windows; o último é o 17.
A matriz real é: x86 de 8 a 17, x64 de 8 até a mais recente.

**Decisão proposta (pendente).** Trocar o Skia por NanoVG para formas, HarfBuzz + FreeType para
texto e Yoga para layout, todos via LWJGL, cobrindo a matriz inteira com um único renderer.
Alternativa descartada: Skia em 64 bits e NanoVG em 32 bits, por exigir dois renderers e gerar
diferenças visuais.

**Cuidados de build identificados.** Compilar com `--release 8` (compilar num JDK 9+ só com
`source`/`target` 1.8 gera chamadas como `ByteBuffer.flip()` com assinatura nova, que quebram no
Java 8). No JDK 24+, carregar código nativo emite aviso (JEP 472); usar
`--enable-native-access=ALL-UNNAMED`.

---

## 2026-09-24: prioridades arquiteturais para aplicações grandes

Com o objetivo definido, o gargalo deixou de ser o motor gráfico e passou a ser a arquitetura:

1. Árvore retida com atualização incremental (hoje a árvore inteira é reconstruída a cada mudança).
2. Redesenho só das regiões alteradas e cache de camadas.
3. Listas, tabelas e árvores virtualizadas desde o início.
4. Layout e medição de texto fora da thread de UI.
5. Qualidade de texto igual ou superior à do JavaFX (shaping e rasterização).

---

## 2026-09-24 e 25: carregamento de FXML com controllers

**Estratégia.** Comparar o uso típico do JavaFX (`FXMLLoader.load` sequencial na thread do FX)
com `FXMLLoaderService.loadAsync` do JXParallel (pool de workers e cache de FXML). 20 telas
geradas (formulário com barra de ferramentas, 3 abas com 15 campos cada, cerca de 135 elementos,
controller com 21 campos `@FXML` e `initialize()`). Três passadas por JVM: a primeira fria,
a terceira quente. Cada passada confere que os 20 controllers executaram. Commit `a955d1c`.

**Bugs encontrados no core durante o teste** (commit `9dc258c`):
1. O `AdaptiveWorkerPool` nunca passava de `threads.min` (2 threads). O `ThreadPoolExecutor` só
   cria threads acima do core quando a fila recusa uma tarefa, e a fila tinha milhares de vagas.
   A opção `threads.auto-scale` era lida e ignorada. Correção: com auto-scale, core igual a
   `threads.max` e threads ociosas expiram após o keep-alive.
2. A fila limitada não devolvia a permissão em `poll(timeout)`, método usado por threads que
   podem expirar. As permissões vazavam até a fila parecer cheia e rejeitar tarefas.
   Um teste (`AdaptiveWorkerPoolScalingTest`) falha sem a correção e passa com ela.

**Escalabilidade por número de workers** (passada quente, mediana de 3):
1 thread 1221 ms, 2 threads 1170 ms, 4 threads 619 ms, 8 threads 442 ms.

**Resultado com a máquina ociosa** (2026-09-25, mediana de 5, [fxml-load-comparison.md](../fxml-load-comparison.md)):

| Métrica | JavaFX | JXParallel | Diferença |
|---|---:|---:|---:|
| 20 telas, fria | 1163 ms | 725 ms | 1.6x mais rápido |
| 20 telas, quente | 665 ms | 194 ms | 3.4x mais rápido |
| Thread de UI bloqueada (fria) | 1163 ms | 0 ms | |
| Primeira tela pronta, quente | 30 ms | 72 ms | 2.4x mais lento |
| Pico de RAM | 319 MB | 395 MB | +24% |
| Alocação por passada quente | 213 MB | 215 MB | igual |

**Achados.**
- O cache de FXML guardava só os bytes do arquivo e não trouxe ganho: o custo está no parse do
  XML, na reflexão para criar nós e na injeção `@FXML`, não na leitura do arquivo.
- Uma bateria feita com 27 a 42% da CPU ocupada por outros processos mostrou "+54% de CPU" no
  JXParallel. Com a máquina ociosa, a CPU empatou (1.98 s contra 1.86 s). A bateria ruidosa foi
  descartada. Lição: controlar a carga da máquina é obrigatório.

**Estratégia "primeira tela primeiro"** (2026-09-25). A prioridade de tarefas não resolve a
primeira tela lenta: com 8 workers ela já começa imediatamente, mas disputa CPU com outras 7
telas. Nova estratégia: carregar sozinha a tela que o usuário abriu e só depois disparar as
demais em paralelo.

| Métrica (mediana de 5, máquina com 27 a 33% de carga) | JavaFX | JXParallel |
|---|---:|---:|
| Primeira tela, quente | 64 ms | 59 ms |
| Primeira tela, fria | 512 ms | 468 ms |
| 20 telas, quente | 1198 ms | 324 ms (3.7x) |
| 20 telas, fria | 1931 ms | 972 ms (2.0x) |

A primeira tela deixou de ser um ponto fraco e o ganho no lote se manteve.

---

## 2026-09-25: correções rápidas e limpeza

**Build compatível com Java 8 em qualquer JDK.** Profile `release-8`, ativado em JDK 9+, que usa
`maven.compiler.release=8`. Sem ele, compilar num JDK novo com `source`/`target` 1.8 liga o código
à API nova (por exemplo `ByteBuffer.flip()` retornando `ByteBuffer`) e quebra no Java 8.

**Layout.** `column` e `row` dividiam o espaço igualmente entre os filhos: numa janela com um
texto e um botão, o botão ocupava metade da altura. Agora seguem o modelo do `VBox`/`HBox` do
JavaFX: tamanho preferido no eixo principal e preenchimento no eixo cruzado. Teste novo
`columnShouldGiveChildrenTheirPreferredHeightAndFullWidth`.

**Concorrência no `JXObservableList`.** Depois de mover os listeners para fora do lock, `clear()`
lia `size()` e removia aquele índice em passos separados: uma remoção concorrente no meio gerava
`IndexOutOfBoundsException`. `clear()` e `addAll()` voltaram a ser atômicos (mutação inteira sob o
lock, eventos disparados depois). O teste `clearShouldNotRaceWithConcurrentRemovals` falha no
código anterior e passa no novo.

**AWT removido do `jxparallel-ui`.** `JXNativeNode` usa `int` no lugar de `Rectangle`/`Dimension`;
`JXStyle`/`JXTheme` guardam cores como ARGB `int` (formato consumido pelo renderer); o clipboard
do `JXTextDocument` passou a ser a interface `JXClipboard`, com implementação via GLFW no
`JXWindow`. O módulo de UI não importa mais nada de `java.awt` nem `javax.swing`.

**Removidos.** Módulo `jxparallel-lwjgl` (OpenGL cru, sem Skia, redundante depois da troca) e o
relatório de estresse de 2026-09-22 com os números inválidos. O README passou a mostrar os
resultados atuais e a registrar por que o relatório antigo foi retirado.

**Integração contínua.** Matriz ampliada para cobrir o público-alvo: Windows x86 com Java 8 e 21,
Windows x64 com Java 25, além dos jobs existentes. O Java 8 compila só os módulos de runtime
(`core` e `ui`), porque o Mockito 5 exige Java 11.

**Validação.** JDK 8 x86: 36 testes (core e UI). JDK 17 x64: 48 testes em todos os módulos,
incluindo JavaFX e FXML. Nenhuma falha.

---

## 2026-09-25: dois renderers, Skia em 64 bits e NanoVG em 32 bits

**Decisão do autor.** Manter o Skia nas JVMs de 64 bits e usar NanoVG só nas de 32 bits. Motivo:
depois do Java 8 quase ninguém usa 32 bits, e o port Windows x86 do JDK termina no 21. Assim o
caminho principal mantém a qualidade de texto do Skia e o Java 8 x86 passa a ter janela nativa.
Isso substitui a proposta anterior (NanoVG para todos).

**Implementação.** `JXWindow` escolhe o backend em tempo de execução por
`sun.arch.data.model` (32 → NanoVG, senão Skia), com `-Djx.renderer=skia|nanovg` para forçar um
deles. Os backends são classes internas; a do Skia só é carregada em 64 bits, então uma JVM de 32
bits nunca toca no Skija. `JXNanoVGRenderer` desenha as mesmas formas, cores e tamanhos do
`JXSkiaRenderer`, usando NanoVG com contexto OpenGL 3 e uma fonte TrueType do sistema (Segoe UI no
Windows; `-Djx.font` para outra). O pom ganhou um profile de natives Windows x86 só com LWJGL e
NanoVG. Teste novo: `JXWindowRendererTest`.

**Primeira execução em 32 bits.** Pela primeira vez a janela nativa abriu no Java 8 32-bit.
Execução curta (50 atualizações por componente, 60 quadros, sessão bloqueada):

| JVM | Motor | Quadros | Pico de heap |
|---|---|---:|---:|
| Java 8 x86 | NanoVG (automático) | 60 | 5.4 MB |
| Java 17 x64 | Skia (automático) | 60 | 12.0 MB |
| Java 17 x64 | NanoVG (forçado) | 60 | 10.0 MB |

Primeiro contato com JavaFX 8 contra JXParallel/NanoVG, ambos em Java 8 x86 (1 execução, valores
indicativos): pico de heap 7.8 contra 5.5 MB, heap vivo após GC 5.8 contra 2.3 MB, classes
carregadas 2808 contra 1236, primeiro quadro 1936 contra 1053 ms de uptime.

**Verificação visual.** As duas janelas foram capturadas lado a lado
([renderers-nanovg-vs-skia.png](../assets/renderers-nanovg-vs-skia.png)): mesmo layout e cores; o
texto do NanoVG sai um pouco maior (15 px contra 13 px no Skia).

**Baterias completas** (mediana de 5, tela ativa a cerca de 118 Hz, 5 a 23% de carga de fundo,
[ui-comparison-2026-09-25.md](../ui-comparison-2026-09-25.md)):

| Métrica | JavaFX 8 x86 | JXParallel NanoVG x86 | JavaFX 21 x64 | JXParallel Skia x64 |
|---|---:|---:|---:|---:|
| Primeiro quadro (uptime) | 777 ms | 609 ms | 1494 ms | 840 ms |
| Pico de working set | 97.3 MB | 89.3 MB | 247.5 MB | 160.1 MB |
| Heap vivo após GC | 7.6 MB | 2.1 MB | 10.6 MB | 2.1 MB |
| CPU por quadro | 3.9 ms | 1.5 ms | 11.3 ms | 2.9 ms |
| Alocação em 600 quadros | 24.5 MB | 3.9 MB | 34.1 MB | 5.0 MB |
| FPS | 118.2 | 118.5 | 117.3 | 118.7 |
| Pior quadro | 30.4 ms | 40.2 ms | 55.0 ms | 39.1 ms |
| Atualizar label 500x | 13.8 ms | 30.8 ms | 14.7 ms | 17.6 ms |

**Leitura.** Em 32 bits a diferença de working set é pequena (-8%), porque o JavaFX 8 na client VM
já é enxuto; o ganho aparece em bytes privados (-34%), heap e alocação. A CPU por quadro é 2.5 a 4
vezes menor nas duas plataformas, com o mesmo FPS. O label continua mais lento nos dois casos.
O pior quadro do NanoVG (40 ms, um único quadro) precisa de mais execuções para virar conclusão.

**Ferramenta nova.** `-Djx.monitor=N` abre a janela no monitor N, no `JXWindow` e no runner do
JavaFX, e `-Monitor` no script. A bateria x64 rodou no segundo monitor para não atrapalhar o uso
da máquina.

---

## 2026-09-25: atualização incremental da árvore de UI

**Diagnóstico antes de mudar.** Um microbenchmark separou o custo de cada atualização em render
(criar a árvore de `JXElement`), montagem (criar a árvore nativa) e layout. Com os 5 nós do teste,
os três juntos custavam só 6 µs por atualização, contra cerca de 60 µs medidos no teste de
estresse. O custo que faltava vinha de `requestRender()`, que chamava `glfwPostEmptyEvent()` (uma
chamada ao sistema) a cada atualização. Em telas grandes o problema era outro: com 2005 nós, cada
atualização custava 0.25 ms e crescia linearmente com o tamanho da tela.

**Primeira tentativa, insuficiente.** Reconciliação sozinha (`JXNativeNode.reconcile`: reaproveitar
nós do mesmo tipo e só trocar as props) não reduziu o custo com 2005 nós (127 ms contra 122 ms em
500 atualizações), porque o `render()` ainda recriava todos os elementos e a reconciliação
precisava percorrer todos.

**Solução.** Três partes:
1. Pedidos de redesenho agrupados com `AtomicBoolean`: só o primeiro pedido após um quadro acorda
   o laço de renderização.
2. Memoização por controle (`JXRenderMemo`): `render()` devolve a mesma instância enquanto o estado
   lido na hora não muda. A chave é o estado atual, não listeners, então não há como a tela ficar
   desatualizada.
3. Atalho por identidade na reconciliação: se o elemento é a mesma instância do render anterior, a
   subárvore inteira é pulada. O layout só é invalidado quando a estrutura muda. Efeito colateral
   positivo: o nó com foco deixa de ser perdido a cada atualização.

**Microbenchmark** (500 atualizações de label, render + árvore nativa + layout, Java 17):

| Tamanho da tela | Antes | Depois |
|---|---:|---:|
| 5 nós | 5.6 ms | 1.6 ms |
| 205 nós | 17.8 ms | 4.4 ms |
| 2005 nós | 127.4 ms | 30.9 ms |

**Achado metodológico.** A fase de label é a primeira do teste de estresse e roda com o JIT frio. O
botão, que faz mais trabalho e vem depois, era mais rápido que o label. Os dois runners passaram a
repetir a fase de label no fim (`stress_label_warm_ns`), de forma simétrica, sem alterar as métricas
existentes.

**Resultado no teste de estresse** (x86: 10 execuções; x64: 5; carga de fundo 10 a 37%):

| Métrica | JavaFX 8 x86 | JXParallel x86 | JavaFX 21 x64 | JXParallel x64 |
|---|---:|---:|---:|---:|
| Label 500x, JIT frio | 11.4 ms | 10.3 ms | 10.3 ms | 10.7 ms |
| Label 500x, quente | 2.57 ms | 0.93 ms | 3.34 ms | 2.59 ms |
| Rajada total | 147.0 ms | 25.2 ms | 234.8 ms | 33.2 ms |
| Pior quadro | 31.1 ms | 23.6 ms | 56.9 ms | 13.1 ms |
| Quadros acima de 25 ms | 1 | 0 | 2 | 0 |

O label deixou de ser ponto fraco: empate com JIT frio e vantagem com JIT quente.

**Pior quadro do NanoVG.** Com 10 execuções, o pior quadro do NanoVG ficou entre 21.1 e 25.2 ms em
todas, com zero quadros acima de 25 ms em 9 delas. Os 40.2 ms da bateria anterior foram um caso
isolado. Não houve mudança de código específica para isso.

---

## 2026-09-25: cache de FXML com template pré-interpretado

**Problema.** O cache antigo guardava os bytes do arquivo, e as passadas quentes alocavam o mesmo
que o JavaFX (cerca de 223 MB para 20 telas). O custo estava no parse do XML, nas buscas por
reflexão e na maquinaria do `FXMLLoader` (builders, expressões), repetidos a cada carga.

**Estratégia.** `FxmlTemplate`: na primeira carga, o FXML é lido com DOM e vira um plano imutável,
com classes resolvidas, construtor escolhido (inclusive `@NamedArg`, preferindo `int` a `double`
quando o valor permite), setters, propriedades estáticas (`GridPane.rowIndex`), propriedade
padrão (`@DefaultProperty`), campos `@FXML` do controller e métodos de evento. Os valores dos
atributos já são convertidos nessa etapa. As cargas seguintes só chamam construtores e setters.
O que o template não implementa (`fx:include`, `fx:define`, expressões, `%recursos`,
`@locais`, scripts, texto dentro de elementos) cai no `FXMLLoader`, então nunca fica pior que antes.

**Bug encontrado pela validação.** Na primeira execução, o template parecia 20x mais rápido, mas
nenhuma tela passava na checagem de controller inicializado. O `FXMLLoader` também copia o `fx:id`
para o `id` do nó (é isso que faz `lookup("#status")` funcionar), e o template não fazia isso.
Depois da correção, foi acrescentada ao benchmark uma impressão digital estrutural: contagem de
todos os nós alcançáveis e somas dos valores dos `Spinner` e dos índices de linha do `GridPane`.
Os três modos deram 2300 nós, 4320 e 12600 em todas as passadas. Lição: um ganho grande demais
pede uma checagem de equivalência, não só de "rodou sem erro".

**Resultado** (mediana de 5; terceiro modo isola o template: mesmo pool com `FXMLLoader` dentro):

| Métrica | JavaFX sequencial | Pool + `FXMLLoader` | Pool + template |
|---|---:|---:|---:|
| 20 telas, quente | 692 ms | 258 ms | 31 ms |
| 20 telas, frio | 1467 ms | 818 ms | 538 ms |
| Primeira tela, quente | 40.7 ms | 36.3 ms | 7.8 ms |
| CPU, passada quente | 2.28 s | 1.95 s | 0.30 s |
| Alocação, passada quente | 223.5 MB | 225.0 MB | 9.8 MB |
| Alocação, passada fria | 245.4 MB | 248.2 MB | 318.7 MB |
| Pico de working set | 330 MB | 412 MB | 322 MB |

A tela individual passou a ser 5x mais rápida que no JavaFX (antes só o lote ganhava), e o pico de
memória deixou de ficar acima do JavaFX. Custo: a primeira carga de cada arquivo aloca mais, e os
templates em cache retêm cerca de 1.5 MB a mais de heap.

---

## Evolução das métricas principais

| Data | Métrica | JavaFX | JXParallel | Observação |
|---|---|---:|---:|---|
| 22/09 | Atualizar label 500x | 13.0 ms | 3.3 ms | teste inválido (não chegava à tela) |
| 24/09 | Atualizar label 500x | 11.6 ms | 30.0 ms | teste corrigido |
| 24/09 | Pico de RAM, teste de UI | 248.6 MB | 159.8 MB | Skia + OpenGL |
| 24/09 | CPU por quadro | 10.6 ms | 3.0 ms | 600 quadros, vsync |
| 24/09 | FXML 20 telas, quente | 1314 ms | 1170 ms | pool preso em 2 threads |
| 24/09 | FXML 20 telas, quente | 1314 ms | 442 ms | pool corrigido, 8 threads |
| 25/09 | FXML 20 telas, quente | 665 ms | 194 ms | máquina ociosa |
| 25/09 | FXML primeira tela, quente | 30 ms | 72 ms | todas em paralelo |
| 25/09 | FXML primeira tela, quente | 64 ms | 59 ms | primeira tela primeiro |
| 25/09 | CPU por quadro, Java 8 x86 | 3.9 ms | 1.5 ms | JavaFX 8 contra NanoVG |
| 25/09 | CPU por quadro, Java 17 x64 | 11.3 ms | 2.9 ms | JavaFX 21 contra Skia, FPS válido |
| 25/09 | Heap vivo após GC, x86 | 7.6 MB | 2.1 MB | JavaFX 8 contra NanoVG |
| 25/09 | Label 500x quente, x64 | 3.34 ms | 2.59 ms | após atualização incremental |
| 25/09 | Label 500x quente, x86 | 2.57 ms | 0.93 ms | após atualização incremental |
| 25/09 | Pior quadro NanoVG x86 | 31.1 ms | 23.6 ms | 10 execuções |
| 25/09 | FXML 20 telas, quente | 692 ms | 31 ms | template pré-interpretado |
| 25/09 | FXML uma tela, quente | 40.7 ms | 7.8 ms | template pré-interpretado |

## Ameaças à validade (para o capítulo de metodologia)

- Os dois lados não desenham a mesma coisa: o JavaFX aplica CSS, skins, texto LCD e um
  `ListView` real; o JXParallel desenha retângulos e texto.
- Carga de outros processos altera os tempos absolutos em até 2x.
- Sessão do Windows bloqueada limita a apresentação de quadros.
- A CPU de processo no Windows tem granularidade de 15.6 ms.
- A alocação é somada só sobre threads vivas no momento da leitura.
- Todos os números até aqui são de uma única máquina.

## Pendências

- Compilação de FXML para Java no build (removeria o custo da primeira carga).
- HarfBuzz/FreeType para texto e Yoga para layout.
- Lista e tabela virtualizadas, com benchmark de 100 mil linhas contra o `TableView`.
- Repetir as baterias com a sessão do Windows desbloqueada e a máquina ociosa (FPS válido).
