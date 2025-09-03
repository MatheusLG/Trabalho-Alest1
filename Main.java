import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        for(int i=1; i<=500; i++){
            String nomeArquivo= String.format("caso%03d.txt", i)
            File arquivo = new File(nomeArquivo);
            if(arquivo.exists()){
                try{
                    lerArquivo(nomeArquivo);
                }
            }
        }
    }

    public static void lerArquivo(String nomeArquivo) throws FileNotFoundException{
    }
}
