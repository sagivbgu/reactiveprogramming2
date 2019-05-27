package com.mlss.whatsapp_client;

import akka.actor.ActorRef;
import com.mlss.whatsapp_common.UserFeatures.ConnectRequest;

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
        switch (commandWords[1]) {
            case "connect":
                runUserConnectCommand(commandWords);
                break;
            case "text":
                runSendTextToUserCommand(commandWords);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    private void runGroupCommand(String[] commandWords) throws IllegalCommandException {
        switch (commandWords[1]) {
            case "":
                // TODO
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


    private void runSendTextToUserCommand(String[] commandWords) throws IllegalCommandException {
        if (commandWords.length < 4) {
            throw new IllegalCommandException();
        }

        String message = String.join(" ",
                Arrays.stream(commandWords, 3, commandWords.length)
                        .toArray(String[]::new));

        this.userActor.tell(new UserActor.SendMessageRequest(commandWords[2], new UserActor.TextMessage(message)), ActorRef.noSender());
    }
}
