import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class Task {
    String name;
    int processingTime;
    int originalInDegree; 
    int inDegree; 
    List<Task> successors = new ArrayList<>(); 

    public Task(String name, int processingTime) {
        this.name = name;
        this.processingTime = processingTime;
    }
    public void reset() {
        this.inDegree = this.originalInDegree;
    }
    @Override
    public String toString() {
        return name + " (" + processingTime + "ms)";
    }
}
class Processor {
    private Task currentTask = null;
    private int timeRemaining = 0;

    public boolean isFree() {
        return currentTask == null;
    }
    public void startTask(Task task) {
        this.currentTask = task;
        this.timeRemaining = task.processingTime;
    }
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
public class CloudProcessorSimulator {
    public static void main(String[] args) {
        System.out.println("### Iniciando Simulador de Processamento em Nuvem ###");
        for (int i = 1; i <= 500; i++) {
            String fileName = String.format("caso%03d.txt", i);
            File testFile = new File(fileName);
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

    public static void runTest(String fileName) throws FileNotFoundException {
        File file = new File(fileName);
        Scanner scanner = new Scanner(file);
        int numProcessors = -1; // Inicializa com um valor inválido

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
        if (numProcessors == -1) {
            System.err.println("ERRO: A definição de processadores (ex: '# Proc 5') não foi encontrada em " + fileName);
            scanner.close();
            return;
        }
        Map<String, Task> tasks = parseTasks(scanner);
        System.out.println("Número de Processadores: " + numProcessors);
        System.out.println("Total de Tarefas: " + tasks.size());
        long timeMin = runSimulation(numProcessors, tasks, MaxMin.MIN);
        System.out.println("-> Tempo total com política MIN: " + timeMin);
        long timeMax = runSimulation(numProcessors, tasks, MaxMin.MAX);
        System.out.println("-> Tempo total com política MAX: " + timeMax);

        scanner.close();
    }
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

        for (Task task : tasks.values()) {
            task.originalInDegree = task.inDegree;
        }
        return tasks;
    }
    public static long runSimulation(int numProcessors, Map<String, Task> tasks, MaxMin policy) {
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
            readyQueue.sort((t1, t2) -> {
                if (policy == MaxMin.MIN) {
                    return Integer.compare(t1.processingTime, t2.processingTime);
                } else { // MAX
                    return Integer.compare(t2.processingTime, t1.processingTime);
                }
            });
            for (Processor p : processors) {
                if (p.isFree() && !readyQueue.isEmpty()) {
                    Task taskToRun = readyQueue.remove(0);
                    p.startTask(taskToRun);
                }
            }
            if (completedTasks == totalTasks) break;
            int timeToAdvance = Integer.MAX_VALUE;
            for (Processor p : processors) {
                if (!p.isFree()) {
                    timeToAdvance = Math.min(timeToAdvance, p.getTimeRemaining());
                }
            }
            if (timeToAdvance == Integer.MAX_VALUE) {
                 System.err.println("ALERTA: Nenhuma tarefa em andamento, mas a simulação não terminou. Verifique o grafo de tarefas.");
                 break;
            }
            currentTime += timeToAdvance;
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
