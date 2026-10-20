package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Server {
    final static int PORT = 3000;
    
    public static void main(String[] args) throws Exception {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            System.out.println("[INFO] listening on " + PORT + " ...");

            while (true) {
                Socket s = ss.accept();
                System.out.println("[INFO] client connected " + s.getInetAddress().toString());
                new Handler(s).start();
            }
        }
    }
}

class Handler extends Thread {
    Socket m_s;
    public Handler(Socket s) {
        m_s = s;
    }

    public void run() {
        try {   
            BufferedReader in = new BufferedReader(
                new InputStreamReader(m_s.getInputStream())
            );
            PrintWriter out = new PrintWriter(m_s.getOutputStream(), true);

            while (true) {
                String s = in.readLine();

                if (s == null || s.trim().equals(":exit")) {
                    System.out.println("[INFO] client disconnected");
                    break;
                }
                
                if (s.trim().isEmpty()) {
                    continue;
                }

                try {
                    Lexer l = new Lexer(s.getBytes());
                    Parser p = new Parser(l.lex());
                    Expr e = p.parse();
                    double res = e.eval();
                    out.println("RES: " + res);
                } catch (Exception e) {
                    out.println("ERR: " + e.getMessage());
                    return;
                }
            }


        } catch (Exception e) {
            System.out.println("[ERR] " + e.getMessage());
        }
    }
}

sealed interface Token permits 
    Token.Number,
    Token.Identifier, 
    Token.Add, 
    Token.Sub,
    Token.Mul,
    Token.Div,
    Token.LParen,
    Token.RParen,
    Token.Comma,
    Token.EOF {

    record Number(double value) implements Token {}
    record Identifier(String name) implements Token {}

    record Add() implements Token {}
    record Sub() implements Token {}
    record Mul() implements Token {}
    record Div() implements Token {}
    record Comma() implements Token {}
    record LParen() implements Token {}
    record RParen() implements Token {}
    record EOF() implements Token {}
}

class Lexer {
    private final byte[] SRC;

    public Lexer(byte[] src) {
        SRC = src;
    }

    public List<Token> lex() {
        int idx = 0;
        ArrayList<Token> out = new ArrayList<>();

        while (idx < SRC.length) {
            byte b = SRC[idx];

            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                idx++;
                continue;
            }

            switch (b) {
                case '+' -> { out.add(new Token.Add()); idx++; continue; }
                case '-' -> { out.add(new Token.Sub()); idx++; continue; }
                case '*' -> { out.add(new Token.Mul()); idx++; continue; }
                case '/' -> { out.add(new Token.Div()); idx++; continue; }
                case '(' -> { out.add(new Token.LParen()); idx++; continue; }
                case ')' -> { out.add(new Token.RParen()); idx++; continue; }
                case ',' -> { out.add(new Token.Comma()); idx++; continue; }
            }

            if (isNum(b)) {
                int start = idx;

                while (idx < SRC.length && isNum(SRC[idx])) {
                    idx++;
                }
                if (idx < SRC.length && SRC[idx] == '.') {
                    idx++;
                    while (idx < SRC.length && isNum(SRC[idx])) {
                        idx++;
                    }
                }
                

                String numStr = new String(SRC, start, idx - start, StandardCharsets.UTF_8);
                out.add(new Token.Number(Double.parseDouble(numStr)));
                continue;
            }

            if (isAlpha(b)) {
                int start = idx;
                while (idx < SRC.length && (isAlpha(SRC[idx]) || isNum(SRC[idx]))) {
                    idx++;
                }
                
                String idStr = new String(SRC, start, idx - start, StandardCharsets.UTF_8);
                out.add(new Token.Identifier(idStr));
                continue;
            }

            throw new RuntimeException("unknown char '" + (char)b + "' at index '" + idx + "'");
        }

        return out;
    }

    static boolean isAlpha(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || b == '_';
    }
    
    static boolean isNum(byte b) {
        return b >= '0' && b <= '9';
    }
}

enum Prec {
    LOW,
    ADD,
    MUL,
    CALL,
    PREFIX
}

class Parser {
    final List<Token> TOKENS;
    int idx = 0;
    Token curr = null;

    public Parser(List<Token> t) {
        TOKENS = t;
        next();
    }

    public Expr parse() {
        return parseExpr(Prec.LOW);
    }

    Expr parseExpr(Prec p) {
        Expr left = switch (curr) {
            case Token.Number(double n) -> {
                next();
                yield new Expr.Number(n);
            }
            case Token.Sub() -> {
                next();
                Expr r = parseExpr(Prec.PREFIX);
                yield new Expr.Neg(r);
            }
            case Token.LParen() -> {
                next();
                
                Expr r = parseExpr(Prec.LOW); 
                
                if (curr instanceof Token.RParen) {
                    next();
                    yield r;
                } else {
                    throw new RuntimeException("unclosed parenthesis");
                }
            }
            case Token.Identifier(String s) -> {
                next(); 
                
                if (curr instanceof Token.LParen) {
                    next();
                    List<Expr> args = new ArrayList<>();
                    
                    if (!(curr instanceof Token.RParen)) {
                        while (true) {
                            args.add(parseExpr(Prec.LOW));
                            
                            if (curr instanceof Token.Comma) {
                                next();
                            } else {
                                break;
                            }
                        }
                    }

                    if (curr instanceof Token.RParen) {
                        next(); 
                        yield new Expr.Call(s, args);
                    } else {
                        throw new RuntimeException("Manca la parentesi di chiusura ')' nella chiamata a funzione: " + s);
                    }
                }
                
                yield new Expr.Variable(s);
            }
            default -> {
                throw new RuntimeException("invalid prefix expr");
            }
        };
        while (p.compareTo(getPrec(curr)) < 0) {
            Token op = curr;
            next();

            Prec opPrec = getPrec(op); 

            Expr right = parseExpr(opPrec); 

            left = switch (op) {
                case Token.Add() -> new Expr.Add(left, right);
                case Token.Sub() -> new Expr.Sub(left, right);
                case Token.Mul() -> new Expr.Mul(left, right);
                case Token.Div() -> new Expr.Div(left, right);
                default -> throw new RuntimeException("Operatore infisso inatteso: " + op);
            };
        }

        return left;
    }

    Prec getPrec(Token c) {
        switch (c) {
            case Token.Add(), Token.Sub() -> {
                return Prec.ADD;
            }
            case Token.Mul(), Token.Div() -> {
                return Prec.MUL;
            }
            case Token.LParen() -> {
                return Prec.CALL;
            }
            default -> {
                return Prec.LOW;
            }
        }
    }

    boolean next() {
        if (idx == TOKENS.size()) {
            curr = new Token.EOF();
            return false;
        }
        curr = TOKENS.get(idx++);
        return true;
    }
}


sealed interface Expr permits 
    Expr.Number, 
    Expr.Add, 
    Expr.Sub, 
    Expr.Mul, 
    Expr.Div, 
    Expr.Call,
    Expr.Neg,
    Expr.Variable {

    double eval();

    record Number(double val) implements Expr {
        public double eval() { return val; }
    }
    record Neg(Expr r) implements Expr {
        public double eval() { return -r.eval(); }
    }


    record Add(Expr left, Expr right) implements Expr {
        public double eval() { return left.eval() + right.eval(); }
    }

    record Sub(Expr left, Expr right) implements Expr {
        public double eval() { return left.eval() - right.eval(); }
    }

    record Mul(Expr left, Expr right) implements Expr {
        public double eval() { return left.eval() * right.eval(); }
    }

    record Div(Expr left, Expr right) implements Expr {

        public double eval() { 
            double denominator = right.eval();
            if (denominator == 0) throw new ArithmeticException("division by zero!");
            return left.eval() / denominator; 
        }
    }

    record Variable(String name) implements Expr {
        public double eval() {
            if (name.equals("pi")) return Math.PI;
            if (name.equals("e")) return Math.E;
            throw new RuntimeException("Variabile non definita: " + name);
        }
    }

    record Call(String name, List<Expr> args) implements Expr {
        public double eval() {
            double[] evaluatedArgs = args.stream().mapToDouble(Expr::eval).toArray();
            
            switch (name) {
                case "min" -> {
                    if (evaluatedArgs.length < 2) throw new IllegalArgumentException("function 'min' wants 2 arguments");
                    return Math.min(evaluatedArgs[0], evaluatedArgs[1]);
                }
                case "max" -> {
                    if (evaluatedArgs.length < 2) throw new IllegalArgumentException("function 'max' wants 2 arguments");
                    return Math.max(evaluatedArgs[0], evaluatedArgs[1]);
                }
                case "sqrt" -> {
                    if (evaluatedArgs.length != 1) throw new IllegalArgumentException("function 'sqrt' wants 1 arguments");
                    return Math.sqrt(evaluatedArgs[0]);
                }
                case "pow" -> {
                    if (evaluatedArgs.length < 2) throw new IllegalArgumentException("function 'pow' wants 2 arguments");
                    return Math.pow(evaluatedArgs[0], evaluatedArgs[1]);
                }
                default -> throw new UnsupportedOperationException("unknown function: '" + name + "'");
            }
        }
    }
}