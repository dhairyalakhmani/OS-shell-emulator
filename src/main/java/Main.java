import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;
            String[] parts = input.split(" ", 2);
            String command = parts[0];
            String arguments = parts.length > 1 ? parts[1] : "";
            if (command.equals("exit")) break;
            else if (command.equals("echo")) System.out.println(arguments);
            else System.out.println(command + ": command not found");
        }
    }
}