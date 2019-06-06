package com.mlss.whatsapp_client;

import akka.actor.ActorRef;
import com.mlss.whatsapp_common.UserFeatures.*;
import com.mlss.whatsapp_common.ManagerCommands.*;
import com.mlss.whatsapp_common.GroupMessages.*;

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
            case "Yes":
            case "No":
                runInviteResponseCommand(commandWords);
                break;
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

    private void runInviteResponseCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length != 1) {
            throw new IllegalCommandException();
        }

        this.userActor.tell(new GroupInviteResponse(commandWords[0]), ActorRef.noSender());
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

        String target = commandWords[2];
        String message = joinWords(commandWords, 3);
        this.userActor.tell(new SendMessageRequest(target, new TextMessage(message)), ActorRef.noSender());
    }

    private void runSendFileToUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 4) {
            throw new IllegalCommandException();
        }

        String target = commandWords[2];
        String filePath = joinWords(commandWords, 3);
        BinaryMessage binaryMessage = getBinaryMessage(filePath);

        if (binaryMessage != null) {
            this.userActor.tell(new SendMessageRequest(target, binaryMessage), ActorRef.noSender());
        }
    }

    private BinaryMessage getBinaryMessage(String filePath) {
        if (Files.notExists(Paths.get(filePath))) {
            System.out.println(String.format("%s does not exist!", filePath));
            return null;
        }

        String fileName = Paths.get(filePath).getFileName().toString();

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            return new BinaryMessage(fileBytes, fileName);
        } catch (IOException e) {
            System.out.println(String.format("Error reading file %s", filePath));
            return null;
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
            case "leave":
                runGroupLeaveCommand(commandWords);
                break;
            case "send":
                runGroupSendCommand(commandWords);
                break;
            case "user":
                runGroupUserCommand(commandWords);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runGroupCreateCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length > 3) {
            System.out.println("Group name can't contain spaces");
        }
        if (commandWords.length != 3) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[2];
        this.userActor.tell(new CreateGroupRequest(groupName), ActorRef.noSender());
    }

    private void runGroupLeaveCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length != 3) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[2];
        this.userActor.tell(new LeaveGroupRequest(groupName), ActorRef.noSender());
    }

    private void runGroupSendCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 3) {
            throw new IllegalCommandException();
        }

        if (commandWords[2].equals("text")) {
            runGroupSendTextCommand(commandWords);
        } else if (commandWords[2].equals("file")) {
            runGroupSendFileCommand(commandWords);
        } else {
            throw new IllegalCommandException();
        }
    }

    private void runGroupSendTextCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 5) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[3];
        TextMessage message = new TextMessage(joinWords(commandWords, 4));
        this.userActor.tell(new GroupSendMessage(groupName, message), ActorRef.noSender());
    }

    private void runGroupSendFileCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 5) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[3];
        String filePath = joinWords(commandWords, 4);
        BinaryMessage binaryMessage = getBinaryMessage(filePath);

        if (binaryMessage != null) {
            this.userActor.tell(new GroupSendMessage(groupName, binaryMessage), ActorRef.noSender());
        }
    }

    private void runGroupUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 5) {
            throw new IllegalCommandException();
        }

        String groupUserCommand = commandWords[2];
        String groupName = commandWords[3];
        String targetUsername = commandWords[4];

        switch (groupUserCommand) {
            case "invite":
                this.userActor.tell(new GroupInviteUserCommand(groupName, targetUsername), ActorRef.noSender());
                break;
            case "remove":
                this.userActor.tell(new GroupRemoveUserCommand(groupName, targetUsername), ActorRef.noSender());
                break;
            case "mute":
                runGroupMuteCommand(commandWords);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runGroupMuteCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length != 6) {
            throw new IllegalCommandException();
        }

        String groupName = commandWords[3];
        String targetUsername = commandWords[4];
        double timeInSeconds;
        try {
            timeInSeconds = Double.parseDouble(commandWords[5]);
        } catch (NumberFormatException e) {
            throw new IllegalCommandException();
        }
        if (timeInSeconds < 0) {
            throw new IllegalCommandException();
        }

        this.userActor.tell(new MuteUserCommand(groupName, targetUsername, timeInSeconds), ActorRef.noSender());
    }

    private String joinWords(String[] commandWords, int fromIndex) {
        return String.join(" ",
                Arrays.stream(commandWords, fromIndex, commandWords.length)
                        .toArray(String[]::new));
    }
}
