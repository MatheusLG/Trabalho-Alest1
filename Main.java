import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class Tarefa {
    String nome;
    int tempProcesso;
    int originalInDegree; 
    int inDegree; 
    List<Tarefa> sucessor = new ArrayList<>(); 

    public Tarefa(String nome, int tempProcesso) {
        this.nome = nome;
        this.tempProcesso = tempProcesso;
    }
    public void reset() {
        this.inDegree = this.originalInDegree;
    }
    @Override
    public String toString() {
        return nome + " (" + tempProcesso + "ms)";
    }
}
class Processo {
    private Tarefa tarefaAtual = null;
    private int tempoRestante = 0;

    public boolean isFree() {
        return tarefaAtual == null;
    }
    public void iniciarTarefa(Tarefa tarefa) {
        this.tarefaAtual = tarefa;
        this.tempoRestante = tarefa.tempProcesso;
    }
    public Tarefa avancarTempo(int tempo) {
        if (tarefaAtual == null) {
            return null;
        }
        this.tempoRestante -= tempo;
        if (this.tempoRestante <= 0) {
            Tarefa tarefaConcluida = this.tarefaAtual;
            this.tarefaAtual = null;
            this.tempoRestante = 0;
            return tarefaConcluida;
        }
        return null;
    }
    public int getTempoRestante() {
        return this.tempoRestante;
    }
}
public class Main {
    public static void main(String[] args) {
        for(int i=1; i<=500; i++){
            String nomeArquivo= String.format("caso%03d.txt", i);
            File arquivo = new File(nomeArquivo);
            if(arquivo.exists()){
                try{
                    lerArquivo(nomeArquivo);
                } catch(Exception e) {
                    System.err.println("Erro ao processar " + nomeArquivo + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static void lerArquivo(String nomeArquivo) throws FileNotFoundException{
        File arquivo = new File(nomeArquivo);
        Scanner in= new Scanner(arquivo);
        int numProcessadores = -1;

        while(in.hasNextLine()){
            String linha= in.nextLine().trim();
            if(linha.isEmpty() || !linha.toLowerCase().contains("proc")){
                continue;
            }
            String valor= linha.replaceAll("[^0-9]", "");
            if(!valor.isEmpty()) {
                numProcessadores = Integer.parseInt(valor);
                break;
            }
        }
        if(numProcessadores == -1) {
            System.err.println("definicao de quantidade de processadores nao encontrada em " + nomeArquivo);
            in.close();
            return;
        }
        Map<String, Tarefa> tarefas = parseTarefa(in);
        System.out.println("Número de Processadores: " + numProcessadores);
        System.out.println("Total de Nodos: " + tarefas.size());
        long tempoMin = calculoProcessos(numProcessadores, tarefas, MaxMin.MIN);
        System.out.println("-> Tempo total com política MIN: " + tempoMin);
        long tempoMax = calculoProcessos(numProcessadores, tarefas, MaxMin.MAX);
        System.out.println("-> Tempo total com política MAX: " + tempoMax);

        in.close();
    }
    private static Map<String, Tarefa> parseTarefa(Scanner in) {
        Map<String, Tarefa> tarefas = new HashMap<>();
        Pattern pattern = Pattern.compile("(\\w+)_(\\d+)");

        while (in.hasNextLine()) {
            String linha = in.nextLine().trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            String[] parts = linha.split("->");
            String predecessorNome = parts[0].trim();
            
            Matcher predecessorMatcher = pattern.matcher(predecessorNome);
            if (!predecessorMatcher.find()) continue;

            String predNome = predecessorMatcher.group(1);
            int predTempo = Integer.parseInt(predecessorMatcher.group(2));
            Tarefa predecessor = tarefas.computeIfAbsent(predNome, k -> new Tarefa(predNome, predTempo));

            if (parts.length > 1) {
                String sucessorNomePadrao = parts[1].trim();
                 Matcher sucessorMatcher = pattern.matcher(sucessorNomePadrao);
                if (!sucessorMatcher.find()) continue;

                String sucessorNome = sucessorMatcher.group(1);
                int sucessorTempo = Integer.parseInt(sucessorMatcher.group(2));
                Tarefa sucessor = tarefas.computeIfAbsent(sucessorNome, k -> new Tarefa(sucessorNome, sucessorTempo));

                predecessor.sucessor.add(sucessor);
                sucessor.inDegree++;
            }
        }
        for (Tarefa tarefa : tarefas.values()) {
            tarefa.originalInDegree = tarefa.inDegree;
        }
        return tarefas;
    }

    public static long calculoProcessos(int numProcessadores, Map<String, Tarefa> tarefas, MaxMin policy) {
        tarefas.values().forEach(Tarefa::reset);
        Processo[] processo = new Processo[numProcessadores];
        for (int i = 0; i < numProcessadores; i++) {
            processo[i] = new Processo();
        }
        List<Tarefa> readyQueue = tarefas.values().stream()
                .filter(t -> t.inDegree == 0)
                .collect(Collectors.toList());
        int tarefasConcluidas = 0;
        long tempoAtual = 0;
        int totalTarefas = tarefas.size();
        while (tarefasConcluidas < totalTarefas) {
            readyQueue.sort((t1, t2) -> {
                if (policy == MaxMin.MIN) {
                    return Integer.compare(t1.tempProcesso, t2.tempProcesso);
                } else { // MAX
                    return Integer.compare(t2.tempProcesso, t1.tempProcesso);
                }
            });
            for (Processo p : processo) {
                if (p.isFree() && !readyQueue.isEmpty()) {
                    Tarefa tarefaPronta = readyQueue.remove(0);
                    p.iniciarTarefa(tarefaPronta);
                }
            }
            if (tarefasConcluidas == totalTarefas) break;
            int tempoParaAvancar = Integer.MAX_VALUE;
            for (Processo p : processo) {
                if (!p.isFree()) {
                    tempoParaAvancar = Math.min(tempoParaAvancar, p.getTempoRestante());
                }
            }
            if (tempoParaAvancar == Integer.MAX_VALUE) {
                 System.err.println("ALERTA: Nenhuma tarefa em andamento, mas a simulação não terminou. Verifique o grafo de tarefas.");
                 break;
            }
            tempoAtual += tempoParaAvancar;
            for (Processo p : processo) {
                Tarefa tarefaConcluida = p.avancarTempo(tempoParaAvancar);
                if (tarefaConcluida != null) {
                    tarefasConcluidas++;
                    for (Tarefa sucessor : tarefaConcluida.sucessor) {
                        sucessor.inDegree--;
                        if (sucessor.inDegree == 0) {
                            readyQueue.add(sucessor);
                        }
                    }
                }
            }
        }
        return tempoAtual;
    }
}
