import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Set<String> set = new HashSet<>();
        set.add("exit");
        set.add("echo");
        set.add("type");
        while (true) {
            System.out.print("$ ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            String[] parts = input.split(" ", 2);
            String command = parts[0];
            String arguments = parts.length > 1 ? parts[1] : "";
            if (command.equals("exit")) break;
            else if (command.equals("echo")) System.out.println(arguments);
            else if(command.equals("type")){
                if(set.contains(arguments)) System.out.println(arguments + " is a shell builtin");
                else {
                    String path = System.getenv("PATH");
                    if(path != null && !path.isEmpty()){
                        String[] directories = path.split(File.pathSeparator);
                        hasPath(directories, arguments);
                    }
                }
            }
            else System.out.println(command + ": command not found");
        }
    }
    private static void hasPath(String[] directories, String arguments){
        boolean found = false;
        for(String dir: directories){
            Path fullPath = Path.of(dir, arguments);
            if(Files.exists(fullPath) && Files.isExecutable(fullPath)){
                System.out.println(arguments + " is " + fullPath.toString());
                found = true;
                break;
            }
        }
        if(!found) System.out.println(arguments + ": not found");
    }
}