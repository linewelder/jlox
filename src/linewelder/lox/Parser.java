package linewelder.lox;

import java.util.*;
import java.util.function.Supplier;

import static linewelder.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse(boolean replPrompt) {
        final List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration(replPrompt));
        }

        return statements;
    }

    private Stmt declaration(boolean replPrompt) {
        try {
            if (match(VAR)) return varDeclaration();
            return statement(replPrompt);
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        final Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer =  null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement(boolean replPrompt) {
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement(replPrompt);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        final Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        final Stmt thenBranch = statement(false);
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement(false);
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        final Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement(boolean replPrompt) {
        final Expr expr = expression();
        if (match(SEMICOLON)) {
            return new Stmt.Expression(expr);
        }

        if (!replPrompt) {
            throw error(peek(), "Expect ';' after expression.");
        }

        if (isAtEnd()) {
            return new Stmt.Print(expr);
        }

        throw error(peek(), "Unexpected token after expression.");
    }

    private List<Stmt> block() {
        final List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration(false));
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        final Expr expr = ternary();

        if (match(EQUAL)) {
            final Token equals = previous();
            final Expr value = assignment();
            if (expr instanceof Expr.Variable name) {
                return new Expr.Assign(name.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr ternary() {
        final Expr expr = equality();
        if (match(QUESTION)) {
            final Expr ifTrue = equality();
            consume(COLON, "Expect ':' between expressions.");
            final Expr ifFalse = ternary();
            return new Expr.Ternary(expr, ifTrue, ifFalse);
        }

        return expr;
    }

    private Expr equality() {
        return binary(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    private Expr comparison() {
        return binary(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    private Expr term() {
        if (match(PLUS)) {
            error(previous(), "Lox does not support unary '+'.");
        }

        Expr expr = factor();
        while (match(MINUS, PLUS)) {
            final Token operator = previous();
            final Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        return binary(this::unary, SLASH, STAR);
    }

    private Expr binary(Supplier<Expr> operand, TokenType... operators) {
        if (match(operators)) {
            error(previous(), "Is a binary operation, left operand missing.");
        }

        Expr expr = operand.get();
        while (match(operators)) {
            final Token operator = previous();
            final Expr right = operand.get();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            final Token operator = previous();
            final Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            final Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (final TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        while(!isAtEnd()) {
            if (peek().type == SEMICOLON) {
                advance();
                return;
            }

            switch (peek().type) {
                case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RIGHT_BRACE, RETURN -> {
                    return;
                }
            }

            advance();
        }
    }
}
