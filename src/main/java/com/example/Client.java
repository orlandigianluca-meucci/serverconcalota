package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        
        Socket s = new Socket("172.16.125.9",3000);
        
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);

        Scanner scanner = new Scanner(System.in);
        String testo;

        do{
            System.out.println("Inserisci una stringa:");
            testo = scanner.nextLine();
            out.println(testo);
            if (testo == ":exit") {
                System.out.println("Connessione terminata");
                break;
            }
            else{
                String ris = in.readLine();
                System.out.println(ris);
            }
        }   
        while(testo != ":exit");
    }
}