import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Representa uma única tarefa a ser processada.
 * Cada tarefa tem um nome, tempo de processamento, e dependências.
 */
class Task {
    String name;
    int processingTime;
    int originalInDegree; // Armazena o grau de entrada original para resetar a simulação
    int inDegree; // Contador de dependências restantes
    List<Task> successors = new ArrayList<>(); // Tarefas que dependem desta

    public Task(String name, int processingTime) {
        this.name = name;
        this.processingTime = processingTime;
    }

    /**
     * Reseta o grau de entrada para o valor original antes de uma nova simulação.
     */
    public void reset() {
        this.inDegree = this.originalInDegree;
    }

    @Override
    public String toString() {
        return name + " (" + processingTime + "ms)";
    }
}

/**
 * Representa um processador que pode executar tarefas.
 */
class Processor {
    private Task currentTask = null;
    private int timeRemaining = 0;

    /**
     * Verifica se o processador está ocioso.
     * @return true se não estiver executando nenhuma tarefa, false caso contrário.
     */
    public boolean isFree() {
        return currentTask == null;
    }

    /**
     * Atribui uma nova tarefa ao processador.
     * @param task A tarefa a ser executada.
     */
    public void startTask(Task task) {
        this.currentTask = task;
        this.timeRemaining = task.processingTime;
    }

    /**
     * Simula a passagem do tempo para o processador.
     * @param time O tempo a ser avançado.
     * @return A tarefa que foi concluída, ou null se nenhuma foi concluída.
     */
    public Task advanceTime(int time) {
        if (currentTask == null) {
            return null;
        }

        this.timeRemaining -= time;
        if (this.timeRemaining <= 0) {
            Task finishedTask = this.currentTask;
            this.currentTask = null;
            this.timeRemaining = 0;
            return finishedTask;
        }
        return null;
    }
    
    public int getTimeRemaining() {
        return this.timeRemaining;
    }
}

/**
 * Enum para definir a política de escalonamento.
 */
enum SchedulingPolicy {
    MIN, MAX
}

public class CloudProcessorSimulator {

    /**
     * Ponto de entrada principal do programa. Executa a simulação para os casos de teste.
     */
    public static void main(String[] args) {
        System.out.println("### Iniciando Simulador de Processamento em Nuvem ###");
        // Loop modificado para procurar arquivos no formato casoXXX.txt
        for (int i = 1; i <= 500; i++) {
            String fileName = String.format("caso%03d.txt", i);
            File testFile = new File(fileName);

            // Apenas processa o arquivo se ele existir no diretório
            if (testFile.exists()) {
                try {
                    System.out.println("\n======================================================");
                    System.out.println("Processando arquivo de teste: " + fileName);
                    System.out.println("======================================================");
                    runTest(fileName);
                } catch (Exception e) {
                    System.err.println("Ocorreu um erro inesperado ao processar " + fileName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Executa a simulação para um arquivo de teste específico.
     * @param fileName O nome do arquivo a ser lido.
     * @throws FileNotFoundException Se o arquivo não for encontrado.
     */
    public static void runTest(String fileName) throws FileNotFoundException {
        File file = new File(fileName);
        Scanner scanner = new Scanner(file);
        int numProcessors = -1; // Inicializa com um valor inválido

        // Loop para encontrar a linha com a contagem de processadores, ignorando linhas em branco
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || !line.toLowerCase().contains("proc")) {
                continue; // Pula a linha se estiver vazia ou não for a linha de definição
            }
            
            String digits = line.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                numProcessors = Integer.parseInt(digits);
                break; // Encontrou o número de processadores, sai do loop
            }
        }

        // Verifica se a contagem de processadores foi encontrada
        if (numProcessors == -1) {
            System.err.println("ERRO: A definição de processadores (ex: '# Proc 5') não foi encontrada em " + fileName);
            scanner.close();
            return;
        }
        
        Map<String, Task> tasks = parseTasks(scanner);

        System.out.println("Número de Processadores: " + numProcessors);
        System.out.println("Total de Tarefas: " + tasks.size());
        
        // 2. Execução das simulações
        long timeMin = runSimulation(numProcessors, tasks, SchedulingPolicy.MIN);
        System.out.println("-> Tempo total com política MIN: " + timeMin);

        long timeMax = runSimulation(numProcessors, tasks, SchedulingPolicy.MAX);
        System.out.println("-> Tempo total com política MAX: " + timeMax);

        scanner.close();
    }

    /**
     * Analisa o arquivo de entrada e constrói o grafo de tarefas.
     * @param scanner O Scanner para ler os dados.
     * @return Um mapa com todas as tarefas, chaveadas pelo nome.
     */
    private static Map<String, Task> parseTasks(Scanner scanner) {
        Map<String, Task> tasks = new HashMap<>();
        Pattern pattern = Pattern.compile("(\\w+)_(\\d+)");

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("->");
            String predecessorNameRaw = parts[0].trim();
            
            Matcher predMatcher = pattern.matcher(predecessorNameRaw);
            if (!predMatcher.find()) continue;

            String predName = predMatcher.group(1);
            int predTime = Integer.parseInt(predMatcher.group(2));
            Task predecessor = tasks.computeIfAbsent(predName, k -> new Task(predName, predTime));

            if (parts.length > 1) {
                String successorNameRaw = parts[1].trim();
                 Matcher succMatcher = pattern.matcher(successorNameRaw);
                if (!succMatcher.find()) continue;

                String succName = succMatcher.group(1);
                int succTime = Integer.parseInt(succMatcher.group(2));
                Task successor = tasks.computeIfAbsent(succName, k -> new Task(succName, succTime));

                predecessor.successors.add(successor);
                successor.inDegree++;
            }
        }

        // Armazena o grau de entrada original para poder resetar
        for (Task task : tasks.values()) {
            task.originalInDegree = task.inDegree;
        }

        return tasks;
    }

    /**
     * Executa a simulação principal com base na política fornecida.
     * @param numProcessors O número de processadores disponíveis.
     * @param tasks O mapa de todas as tarefas.
     * @param policy A política de escalonamento (MIN ou MAX).
     * @return O tempo total para concluir todas as tarefas.
     */
    public static long runSimulation(int numProcessors, Map<String, Task> tasks, SchedulingPolicy policy) {
        // Reseta o estado das tarefas para uma nova simulação
        tasks.values().forEach(Task::reset);

        Processor[] processors = new Processor[numProcessors];
        for (int i = 0; i < numProcessors; i++) {
            processors[i] = new Processor();
        }

        List<Task> readyQueue = tasks.values().stream()
                .filter(t -> t.inDegree == 0)
                .collect(Collectors.toList());

        int completedTasks = 0;
        long currentTime = 0;
        int totalTasks = tasks.size();

        while (completedTasks < totalTasks) {
            // Ordena a fila de tarefas prontas de acordo com a política
            readyQueue.sort((t1, t2) -> {
                if (policy == SchedulingPolicy.MIN) {
                    return Integer.compare(t1.processingTime, t2.processingTime);
                } else { // MAX
                    return Integer.compare(t2.processingTime, t1.processingTime);
                }
            });

            // Atribui tarefas aos processadores ociosos
            for (Processor p : processors) {
                if (p.isFree() && !readyQueue.isEmpty()) {
                    Task taskToRun = readyQueue.remove(0);
                    p.startTask(taskToRun);
                }
            }

            // Se todas as tarefas terminaram e não há mais nada a processar, saia
            if (completedTasks == totalTasks) break;
            
            // Avança o tempo para o próximo evento (término de uma tarefa)
            int timeToAdvance = Integer.MAX_VALUE;
            for (Processor p : processors) {
                if (!p.isFree()) {
                    timeToAdvance = Math.min(timeToAdvance, p.getTimeRemaining());
                }
            }
            
            // Se não há tarefas rodando mas ainda há tarefas a fazer, algo está errado (ciclo ou grafo desconexo)
            if (timeToAdvance == Integer.MAX_VALUE) {
                 System.err.println("ALERTA: Nenhuma tarefa em andamento, mas a simulação não terminou. Verifique o grafo de tarefas.");
                 break;
            }

            currentTime += timeToAdvance;

            // Processa as tarefas que terminaram
            for (Processor p : processors) {
                Task finishedTask = p.advanceTime(timeToAdvance);
                if (finishedTask != null) {
                    completedTasks++;
                    // Libera as dependências
                    for (Task successor : finishedTask.successors) {
                        successor.inDegree--;
                        if (successor.inDegree == 0) {
                            readyQueue.add(successor);
                        }
                    }
                }
            }
        }

        return currentTime;
    }
}
