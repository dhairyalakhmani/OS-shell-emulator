import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Main {
    static class Job {
        int id;
        Process process;
        String command;

        Job(int id, Process process, String command) {
            this.id = id;
            this.process = process;
            this.command = command;
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Set<String> set = new HashSet<>();
        set.add("exit"); set.add("echo"); set.add("type"); set.add("pwd"); set.add("cd"); set.add("jobs");
        List<Job> backgroundJobs = new ArrayList<>();

        while (true) {
            Map<Integer, Character> preMarkers = new HashMap<>();
            for (int j = 0; j < backgroundJobs.size(); j++) {
                char m = ' ';
                if (j == backgroundJobs.size() - 1) m = '+';
                else if (j == backgroundJobs.size() - 2) m = '-';
                preMarkers.put(backgroundJobs.get(j).id, m);
            }

            List<Job> preSorted = new ArrayList<>(backgroundJobs);
            preSorted.sort(Comparator.comparingInt(j -> j.id));
            List<Job> finished = new ArrayList<>();
            for (Job job : preSorted) {
                if (!job.process.isAlive()) {
                    System.out.println(String.format("[%d]%c  %-24s%s", job.id, preMarkers.get(job.id), "Done", job.command));
                    finished.add(job);
                }
            }
            backgroundJobs.removeAll(finished);

            System.out.print("$ ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            String targetFilePath = "";
            boolean isAppend = false, isError = false, foundPath = false, isBackground = false;
            List<String> parsedInput = parseInput(input);
            int parsedInputSize = parsedInput.size();

            if (parsedInputSize > 0 && parsedInput.get(parsedInputSize - 1).equals("&")) {
                isBackground = true;
                parsedInput.remove(parsedInputSize - 1);
            }

            Set<String> validSTDOperators = new HashSet<>();
            validSTDOperators.add(">"); validSTDOperators.add(">>"); validSTDOperators.add("1>"); validSTDOperators.add("1>>"); validSTDOperators.add("2>"); validSTDOperators.add("2>>");

            for(int i = parsedInput.size() - 1; i >= 0; i--){
                String currentToken = parsedInput.get(i);
                if(validSTDOperators.contains(currentToken)){
                    targetFilePath = parsedInput.get(i + 1);
                    foundPath = true;
                    isAppend = currentToken.endsWith(">>");
                    isError = currentToken.startsWith("2");
                    parsedInput.remove(i + 1);
                    parsedInput.remove(i);
                    break;
                }
            }

            if (foundPath) {
                Path path = Path.of(targetFilePath);
                if (isAppend) {
                    Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }

            if (parsedInput.isEmpty()) continue;

            String command = parsedInput.get(0);
            String arguments = parsedInput.size() > 1 ? parsedInput.get(1) : "";

            if (command.equals("exit")) break;
            else if (command.equals("echo")) writeOutput(String.join(" ", parsedInput.subList(1, parsedInput.size())), foundPath, targetFilePath, isAppend, isError);
            else if(command.equals("jobs")) {
                StringBuilder sb = new StringBuilder();
                List<Job> toRemove = new ArrayList<>();
                Map<Integer, Character> markers = new HashMap<>();
                for (int j = 0; j < backgroundJobs.size(); j++) {
                    char m = ' ';
                    if (j == backgroundJobs.size() - 1) m = '+';
                    else if (j == backgroundJobs.size() - 2) m = '-';
                    markers.put(backgroundJobs.get(j).id, m);
                }
                List<Job> sortedJobs = new ArrayList<>(backgroundJobs);
                sortedJobs.sort(Comparator.comparingInt(j -> j.id));

                for (Job job : sortedJobs) {
                    String status = job.process.isAlive() ? "Running" : "Done";
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(String.format("[%d]%c  %-24s%s", job.id, markers.get(job.id), status, job.command));
                    if (!job.process.isAlive()) toRemove.add(job);
                }
                backgroundJobs.removeAll(toRemove);
                if (sb.length() > 0) writeOutput(sb.toString(), foundPath, targetFilePath, isAppend, isError);
            }
            else if(command.equals("pwd")){
                writeOutput(System.getProperty("user.dir"), foundPath, targetFilePath, isAppend, isError);
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
                    writeOutput("cd: " + arguments + ": No such file or directory", foundPath, targetFilePath, isAppend, isError);
                }
            }
            else if(command.equals("type")){
                if(set.contains(arguments)) writeOutput(arguments + " is a shell builtin", foundPath, targetFilePath, isAppend, isError);
                else {
                    String path = System.getenv("PATH");
                    if(path != null && !path.isEmpty()){
                        String[] directories = path.split(File.pathSeparator);
                        hasPath(directories, arguments, foundPath, targetFilePath, isAppend, isError);
                    }
                }
            }
            else{
                ProcessBuilder processBuilder = new ProcessBuilder(parsedInput);
                processBuilder.inheritIO();
                if (foundPath) {
                    File myFile = new File(targetFilePath);
                    ProcessBuilder.Redirect redirect;
                    if (isAppend) redirect = ProcessBuilder.Redirect.appendTo(myFile);
                    else redirect = ProcessBuilder.Redirect.to(myFile);
                    if (isError) processBuilder.redirectError(redirect);
                    else processBuilder.redirectOutput(redirect);
                }
                try{
                    Process process = processBuilder.start();
                    if(isBackground) {
                        int nextId = 1;
                        while (true) {
                            boolean idFound = false;
                            for (Job j : backgroundJobs) {
                                if (j.id == nextId) { idFound = true; break; }
                            }
                            if (!idFound) break;
                            nextId++;
                        }
                        System.out.println("[" + nextId + "] " + process.pid());
                        backgroundJobs.add(new Job(nextId, process, input));
                    } else {
                        process.waitFor();
                    }
                } catch(java.io.IOException e){
                    writeOutput(command + ": command not found", foundPath, targetFilePath, isAppend, isError);
                }
            }
        }
    }

    private static void writeOutput(String content, boolean foundPath, String targetFilePath, boolean isAppend, boolean isError) throws Exception {
        if (foundPath && !isError) {
            Path path = Path.of(targetFilePath);
            if (isAppend) Files.writeString(path, content + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else Files.writeString(path, content + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            System.out.println(content);
        }
    }

    private static void hasPath(String[] directories, String arguments, boolean foundPath, String targetFilePath, boolean isAppend, boolean isError) throws Exception {
        boolean found = false;
        for(String dir: directories){
            Path fullPath = Path.of(dir, arguments);
            if(Files.exists(fullPath) && Files.isExecutable(fullPath)){
                writeOutput(arguments + " is " + fullPath.toString(), foundPath, targetFilePath, isAppend, isError);
                found = true;
                break;
            }
        }
        if(!found) writeOutput(arguments + ": not found", foundPath, targetFilePath, isAppend, isError);
    }

    private static List<String> parseInput(String input){
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean isSingleQuote = false, isDoubleQuote = false, escapeNext = false;
        for(int i = 0; i < input.length(); i++){
            char c = input.charAt(i);
            if(escapeNext) {
                if(isDoubleQuote && c != '"' && c != '\\' && c != '$'){
                    currentToken.append('\\');
                    currentToken.append(c);
                }
                else currentToken.append(c);
                escapeNext = false;
                continue;
            }
            if(c == '\\' && !isSingleQuote) escapeNext = true;
            else if(c == '\'' && !isDoubleQuote) isSingleQuote = !isSingleQuote;
            else if(c == '\"' && !isSingleQuote) isDoubleQuote = !isDoubleQuote;
            else if(c == ' ' && !isSingleQuote && !isDoubleQuote){
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