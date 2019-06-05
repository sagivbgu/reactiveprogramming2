package com.mlss.whatsapp_client;

import akka.actor.ActorRef;
import com.mlss.whatsapp_common.UserFeatures.*;
import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_client.UserActor.*;
import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

class ExitCommandException extends Exception {
    public ExitCommandException() {
        super();
    }
}

class IllegalCommandException extends Exception {
    public IllegalCommandException() {
        super();
    }
}

public class CommandsExecutor {
    private ActorRef userActor;

    public CommandsExecutor(ActorRef userActor) {
        this.userActor = userActor;
    }

    public void start() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            try {
                run(sc.nextLine());
            } catch (IllegalCommandException e) {
                System.out.println("Illegal command.");
            } catch (ExitCommandException e) {
                break;
            }
        }
    }

    private void run(String command) throws ExitCommandException, IllegalCommandException {
        String[] commandWords = command.split(" ");
        switch (commandWords[0]) {
            case "exit":
                throw new ExitCommandException();
            case "/user":
                runUserCommand(commandWords);
                break;
            case "/group":
                runGroupCommand(commandWords);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 2) {
            throw new IllegalCommandException();
        }

        switch (commandWords[1]) {
            case "connect":
                runUserConnectCommand(commandWords);
                break;
            case "text":
                runSendTextToUserCommand(commandWords);
                break;
            case "file":
                runSendFileToUserCommand(commandWords);
                break;
            case "disconnect":
                runUserDisconnectCommand(commandWords);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runUserConnectCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length != 3) {
            throw new IllegalCommandException();
        }

        this.userActor.tell(new ConnectRequest(commandWords[2]), ActorRef.noSender());
    }

    private void runUserDisconnectCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length != 2) {
            throw new IllegalCommandException();
        }

        this.userActor.tell(new DisconnectRequest(), ActorRef.noSender());
    }

    private void runSendTextToUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 4) {
            throw new IllegalCommandException();
        }

        String message = joinWords(commandWords, 3);

        this.userActor.tell(new SendMessageRequest(commandWords[2], new TextMessage(message)), ActorRef.noSender());
    }

    private void runSendFileToUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 4) {
            throw new IllegalCommandException();
        }

        String filePath = joinWords(commandWords, 3);

        String target = commandWords[2];
        byte[] fileBytes;
        if (Files.notExists(Paths.get(filePath))) {
            System.out.println(String.format("%s does not exist!", filePath));
            return;
        }

        String fileName = Paths.get(filePath).getFileName().toString();

        try {
            fileBytes = Files.readAllBytes(Paths.get(filePath));
            this.userActor.tell(new SendMessageRequest(target, new BinaryMessage(fileBytes, fileName)),
                    ActorRef.noSender());
        } catch (IOException e) {
            System.out.println(String.format("Error reading file %s", filePath));
        }
    }

    private void runGroupCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 2) {
            throw new IllegalCommandException();
        }

        switch (commandWords[1]) {
            case "create":
                runGroupCreateCommand(commandWords);
                break;
            case "send":
                if (commandWords.length < 3) {
                    throw new IllegalCommandException();
                }

                if (commandWords[2].equals("text")) {
                    runGroupSendTextCommand(commandWords);
                } else if (commandWords[2].equals("file")) {
//                    runGroupSendTextCommand(commandWords);
                } else {
                    throw new IllegalCommandException();
                }
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runGroupCreateCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 3) {
            throw new IllegalCommandException();
        }

        String groupName = joinWords(commandWords, 2);
        this.userActor.tell(new CreateGroup(groupName), ActorRef.noSender());
    }

    private void runGroupSendTextCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 5) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[3];
        String message = joinWords(commandWords, 4);
        this.userActor.tell(new GroupSendText(groupName, message), ActorRef.noSender());
    }

    private String joinWords(String[] commandWords, int fromIndex) {
        return String.join(" ",
                Arrays.stream(commandWords, fromIndex, commandWords.length)
                        .toArray(String[]::new));
    }
}
