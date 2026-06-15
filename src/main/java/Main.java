import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Set<String> set = new HashSet<>();
        set.add("exit"); set.add("echo"); set.add("type"); set.add("pwd"); set.add("cd"); set.add("cat");
        while (true) {
            System.out.print("$ ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            List<String> parsedInput = parseInput(input);
            String command = parsedInput.get(0);
            String arguments = parsedInput.size() > 1 ? parsedInput.get(1) : "";
            if (command.equals("exit")) break;
            else if (command.equals("echo")) System.out.println(String.join(" ", parsedInput.subList(1, parsedInput.size())));
            else if(command.equals("pwd")){
                System.out.println(System.getProperty("user.dir"));
            }
            else if(command.equals("cd")){
                Path targetPath;
                if(arguments.equals("~")){
                    targetPath = Path.of(System.getenv("HOME"));
                }
                else if (Path.of(arguments).isAbsolute()) {
                    targetPath = Path.of(arguments).normalize();
                }
                else {
                    Path currentPath = Path.of(System.getProperty("user.dir"));
                    targetPath = currentPath.resolve(arguments).normalize();
                }

                if(Files.exists(targetPath) && Files.isDirectory(targetPath)){
                    System.setProperty("user.dir", targetPath.normalize().toString());
                }else{
                    System.out.println("cd: " + arguments + ": No such file or directory");
                }
            }
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
            else{
                ProcessBuilder processBuilder = new ProcessBuilder(parsedInput);
                processBuilder.inheritIO();
                try{
                    Process process = processBuilder.start();
                    process.waitFor();
                } catch(java.io.IOException e){
                    System.out.println(command + ": command not found");
                }
            }
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
    private static List<String> parseInput(String input){
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean isSingleQuote = false;

        for(int i = 0; i < input.length(); i++){
            char c = input.charAt(i);
            if(c == '\'') isSingleQuote = !isSingleQuote;
            else if(c == ' ' && !isSingleQuote){
                if(!currentToken.isEmpty()){
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            }
            else currentToken.append(c);
        }
        if (!currentToken.isEmpty()) tokens.add(currentToken.toString());
        return tokens;
    }
}