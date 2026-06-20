import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
                    String printCmd = job.command.replaceAll("\\s*&$", "");
                    System.out.println(String.format("[%d]%c  %-24s%s", job.id, preMarkers.get(job.id), "Done", printCmd));
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

            if (parsedInput.isEmpty()) continue;

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

            List<List<String>> pipelineCmds = new ArrayList<>();
            List<String> currentCmd = new ArrayList<>();
            for (String token : parsedInput) {
                if (token.equals("|")) {
                    pipelineCmds.add(currentCmd);
                    currentCmd = new ArrayList<>();
                } else {
                    currentCmd.add(token);
                }
            }
            pipelineCmds.add(currentCmd);

            byte[] builtinBuffer = null;

            for (int i = 0; i < pipelineCmds.size(); i++) {
                List<String> cmd = pipelineCmds.get(i);
                if (cmd.isEmpty()) continue;

                String commandName = cmd.get(0);
                boolean isBuiltin = set.contains(commandName);

                if (isBuiltin) {
                    String output = null;

                    if (commandName.equals("exit")) {
                        System.exit(0);
                    } else if (commandName.equals("echo")) {
                        output = String.join(" ", cmd.subList(1, cmd.size()));
                    } else if (commandName.equals("pwd")) {
                        output = System.getProperty("user.dir");
                    } else if (commandName.equals("cd")) {
                        String arguments = cmd.size() > 1 ? cmd.get(1) : "";
                        Path targetPath;
                        if(arguments.equals("~") || arguments.isEmpty()){
                            targetPath = Path.of(System.getenv("HOME"));
                        } else if (Path.of(arguments).isAbsolute()) {
                            targetPath = Path.of(arguments).normalize();
                        } else {
                            Path currentPath = Path.of(System.getProperty("user.dir"));
                            targetPath = currentPath.resolve(arguments).normalize();
                        }
                        if(Files.exists(targetPath) && Files.isDirectory(targetPath)){
                            System.setProperty("user.dir", targetPath.normalize().toString());
                        } else {
                            output = "cd: " + arguments + ": No such file or directory";
                        }
                    } else if (commandName.equals("type")) {
                        String arguments = cmd.size() > 1 ? cmd.get(1) : "";
                        if(set.contains(arguments)) {
                            output = arguments + " is a shell builtin";
                        } else {
                            String path = System.getenv("PATH");
                            boolean foundExe = false;
                            if(path != null && !path.isEmpty()){
                                String[] directories = path.split(File.pathSeparator);
                                for(String dir: directories){
                                    Path fullPath = Path.of(dir, arguments);
                                    if(Files.exists(fullPath) && Files.isExecutable(fullPath)){
                                        output = arguments + " is " + fullPath.toString();
                                        foundExe = true;
                                        break;
                                    }
                                }
                            }
                            if(!foundExe) output = arguments + ": not found";
                        }
                    } else if (commandName.equals("jobs")) {
                        for (Job job : backgroundJobs) {
                            try { job.process.waitFor(25, TimeUnit.MILLISECONDS); } catch (Exception e) {}
                        }
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
                            String printCmd = job.process.isAlive() ? job.command : job.command.replaceAll("\\s*&$", "");
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(String.format("[%d]%c  %-24s%s", job.id, markers.get(job.id), status, printCmd));
                            if (!job.process.isAlive()) toRemove.add(job);
                        }
                        backgroundJobs.removeAll(toRemove);
                        if (sb.length() > 0) output = sb.toString();
                    }

                    if (i == pipelineCmds.size() - 1) {
                        writeOutput(output, foundPath, targetFilePath, isAppend, isError);
                    } else {
                        if (output != null) {
                            builtinBuffer = (output + "\n").getBytes();
                        } else {
                            builtinBuffer = "\n".getBytes();
                        }
                    }
                } else {
                    List<ProcessBuilder> pbs = new ArrayList<>();
                    int startExternal = i;
                    int endExternal = i;
                    while (endExternal < pipelineCmds.size() && !set.contains(pipelineCmds.get(endExternal).get(0))) {
                        ProcessBuilder pb = new ProcessBuilder(pipelineCmds.get(endExternal));
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        pbs.add(pb);
                        endExternal++;
                    }

                    if (builtinBuffer == null) {
                        pbs.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (endExternal == pipelineCmds.size()) {
                        if (foundPath) {
                            File myFile = new File(targetFilePath);
                            ProcessBuilder.Redirect redirect = isAppend ? ProcessBuilder.Redirect.appendTo(myFile) : ProcessBuilder.Redirect.to(myFile);
                            if (isError) {
                                pbs.get(pbs.size() - 1).redirectError(redirect);
                                pbs.get(pbs.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            } else {
                                pbs.get(pbs.size() - 1).redirectOutput(redirect);
                            }
                        } else {
                            pbs.get(pbs.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                    } else {
                        try {
                            pbs.get(pbs.size() - 1).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        } catch (NoSuchFieldError e) {
                            pbs.get(pbs.size() - 1).redirectOutput(new File("/dev/null"));
                        }
                    }

                    try {
                        List<Process> processes = ProcessBuilder.startPipeline(pbs);

                        if (builtinBuffer != null) {
                            processes.get(0).getOutputStream().write(builtinBuffer);
                            processes.get(0).getOutputStream().flush();
                            processes.get(0).getOutputStream().close();
                            builtinBuffer = null;
                        }

                        Process lastProcess = processes.get(processes.size() - 1);
                        if (isBackground && endExternal == pipelineCmds.size()) {
                            int nextId = 1;
                            while (true) {
                                boolean idFound = false;
                                for (Job j : backgroundJobs) {
                                    if (j.id == nextId) { idFound = true; break; }
                                }
                                if (!idFound) break;
                                nextId++;
                            }
                            System.out.println("[" + nextId + "] " + lastProcess.pid());
                            backgroundJobs.add(new Job(nextId, lastProcess, input));
                        } else {
                            for (Process p : processes) {
                                p.waitFor();
                            }
                        }
                    } catch (Exception e) {
                        writeOutput(pipelineCmds.get(startExternal).get(0) + ": command not found", foundPath, targetFilePath, isAppend, isError);
                    }

                    i = endExternal - 1;
                }
            }
        }
    }

    private static void writeOutput(String content, boolean foundPath, String targetFilePath, boolean isAppend, boolean isError) throws Exception {
        if (content == null) return;
        if (foundPath && !isError) {
            Path path = Path.of(targetFilePath);
            if (isAppend) Files.writeString(path, content + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else Files.writeString(path, content + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            System.out.println(content);
        }
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