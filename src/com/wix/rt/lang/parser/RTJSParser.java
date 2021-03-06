package com.wix.rt.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.javascript.*;
import com.intellij.lang.javascript.parsing.*;
import com.intellij.psi.tree.IElementType;
import com.wix.rt.lang.lexer.RTTokenTypes;

/**
 * @author Dennis.Ushakov
 */
public class RTJSParser extends JavaScriptParser<RTJSParser.RTExpressionParser, StatementParser, FunctionParser, JSPsiTypeParser> {
    public RTJSParser(PsiBuilder builder) {
        super(JavaScriptSupportLoader.JAVASCRIPT_1_5, builder);
        myExpressionParser = new RTExpressionParser();
        myStatementParser = new StatementParser<RTJSParser>(this) {
            @Override
            protected void doParseStatement(boolean canHaveClasses) {
                final IElementType firstToken = builder.getTokenType();
                if (firstToken == JSTokenTypes.LBRACE) {
                    parseExpressionStatement();
                    checkForSemicolon();
                    return;
                }
                if (isIdentifierToken(firstToken)) {
                    final IElementType nextToken = builder.lookAhead(1);
                    if (nextToken == JSTokenTypes.IN_KEYWORD) {
                        parseInStatement();
                        return;
                    }
                }
                if (builder.getTokenType() == JSTokenTypes.LPAR) {
                    if (parseInStatement()) {
                        return;
                    }
                }
                super.doParseStatement(canHaveClasses);
            }

            private boolean parseInStatement() {
                PsiBuilder.Marker statement = builder.mark();
                if (!getExpressionParser().parseInExpression()) {
                    statement.drop();
                    return false;
                }
                statement.done(JSElementTypes.EXPRESSION_STATEMENT);
                return true;
            }
        };
    }

    public void parseRT(IElementType root) {
        final PsiBuilder.Marker rootMarker = builder.mark();
        while (!builder.eof()) {
            getStatementParser().parseStatement();
        }
        rootMarker.done(root);
    }

    protected class RTExpressionParser extends ExpressionParser<RTJSParser> {
        public RTExpressionParser() {
            super(RTJSParser.this);
        }

        @Override
        protected boolean parseUnaryExpression() {
            final IElementType tokenType = builder.getTokenType();
            if (tokenType == JSTokenTypes.OR) {
                builder.advanceLexer();
                if (!parseFilter()) {
                    builder.error("expected filter");
                }
                return true;
            }
            if (tokenType == RTTokenTypes.ONE_TIME_BINDING) {
                final PsiBuilder.Marker expr = builder.mark();
                builder.advanceLexer();
                if (!super.parseUnaryExpression()) {
                    builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
                }
                expr.done(JSElementTypes.PREFIX_EXPRESSION);
                return true;
            }
            return super.parseUnaryExpression();
        }

        @Override
        public boolean parsePrimaryExpression() {
            final IElementType firstToken = builder.getTokenType();
            if (firstToken == JSTokenTypes.STRING_LITERAL) {
                return parseStringLiteral(firstToken);
            }
            if (firstToken == JSTokenTypes.IDENTIFIER && builder.lookAhead(1) == JSTokenTypes.AS_KEYWORD) {
                return parseAsExpression();
            }
            return super.parsePrimaryExpression();
        }

        private boolean parseAsExpression() {
            PsiBuilder.Marker expr = builder.mark();
            buildTokenElement(JSElementTypes.REFERENCE_EXPRESSION);
            builder.advanceLexer();
            parseExplicitIdentifierWithError();
            expr.done(RTElementTypes.AS_EXPRESSION);
            return true;
        }

        private void parseExplicitIdentifierWithError() {
            if (isIdentifierToken(builder.getTokenType())) {
                parseExplicitIdentifier();
            } else {
                builder.error(JSBundle.message("javascript.parser.message.expected.identifier"));
            }
        }

        @Override
        protected int getCurrentBinarySignPriority(boolean allowIn, boolean advance) {
            if (builder.getTokenType() == JSTokenTypes.OR) return 10;
            return super.getCurrentBinarySignPriority(allowIn, advance);
        }

        private boolean parseFilter() {
            final PsiBuilder.Marker mark = builder.mark();
            buildTokenElement(JSElementTypes.REFERENCE_EXPRESSION);
            PsiBuilder.Marker arguments = null;
            while (builder.getTokenType() == JSTokenTypes.COLON) {
                arguments = arguments == null ? builder.mark() : arguments;
                builder.advanceLexer();
                if (!super.parseUnaryExpression()) {
                    builder.error(JSBundle.message("javascript.parser.message.expected.expression"));
                }
            }
            if (arguments != null) {
                arguments.done(JSElementTypes.ARGUMENT_LIST);
            }
            mark.done(RTElementTypes.FILTER_EXPRESSION);
            return true;
        }

        private boolean parseStringLiteral(IElementType firstToken) {
            final PsiBuilder.Marker mark = builder.mark();
            IElementType currentToken = firstToken;
            StringBuilder literal = new StringBuilder();
            while (currentToken == JSTokenTypes.STRING_LITERAL ||
                    currentToken == RTTokenTypes.ESCAPE_SEQUENCE ||
                    currentToken == RTTokenTypes.INVALID_ESCAPE_SEQUENCE) {
                literal.append(builder.getTokenText());
                builder.advanceLexer();
                currentToken = builder.getTokenType();
            }
            mark.done(JSElementTypes.LITERAL_EXPRESSION);
            final String errorMessage = validateLiteralText(literal.toString());
            if (errorMessage != null) {
                builder.error(errorMessage);
            }
            return true;
        }

        public boolean parseInExpression() {
            final PsiBuilder.Marker expr = builder.mark();
            if (isIdentifierToken(builder.getTokenType())) {
                parseExplicitIdentifier();
            } else {
                final PsiBuilder.Marker keyValue = builder.mark();
                parseKeyValue();
                if (JSTokenTypes.IN_KEYWORD.equals(builder.getTokenType())) {
                    keyValue.done(JSElementTypes.PARENTHESIZED_EXPRESSION);
                } else {
                    expr.rollbackTo();
                    return false;
                }
            }
            builder.advanceLexer();
            parseExpression();
//            if (builder.getTokenType() == RTTokenTypes.TRACK_BY_KEYWORD) {
//                builder.advanceLexer();
//                parseExpression();
//            }
            expr.done(RTElementTypes.REPEAT_EXPRESSION);
            return true;
        }

        private void parseKeyValue() {
            builder.advanceLexer();
            final PsiBuilder.Marker comma = builder.mark();
            parseExplicitIdentifierWithError();
            if (builder.getTokenType() == JSTokenTypes.COMMA) {
                builder.advanceLexer();
            } else {
                builder.error(JSBundle.message("javascript.parser.message.expected.comma"));
            }
            parseExplicitIdentifierWithError();
            comma.done(JSElementTypes.COMMA_EXPRESSION);
            if (builder.getTokenType() == JSTokenTypes.RPAR) {
                builder.advanceLexer();
            } else {
                builder.error(JSBundle.message("javascript.parser.message.expected.rparen"));
            }
        }

        private void parseExplicitIdentifier() {
            final PsiBuilder.Marker def = builder.mark();
            buildTokenElement(JSElementTypes.REFERENCE_EXPRESSION);
            def.done(JSElementTypes.DEFINITION_EXPRESSION);
        }
    }
}
