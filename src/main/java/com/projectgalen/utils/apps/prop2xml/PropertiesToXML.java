package com.projectgalen.utils.apps.prop2xml;

// ===========================================================================
//     PROJECT: PGUtils
//    FILENAME: PropertiesToXML.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: February 01, 2023
//
// Copyright © 2023 Project Galen. All rights reserved.
//
// Permission to use, copy, modify, and distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
// IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
// ===========================================================================

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public final class PropertiesToXML {
    private final Map<String, String> xmlChars;
    private final ResourceBundle      msgs;
    private final Properties          xmlProps;
    private final Pattern             commentPattern;
    private final boolean             quotesInValues;
    private final String              xmlQuote;
    private final String              lineFormat;
    private final String              keyFormat;
    private final String              doubleDash;
    private final String              doubleDashReplacement;
    private final String              tab;

    public PropertiesToXML() {
        try {
            msgs     = ResourceBundle.getBundle("com.projectgalen.utils.apps.prop2xml.pg_messages");
            xmlProps = new Properties();
            xmlChars = new TreeMap<>();

            xmlProps.loadFromXML(PropertiesToXML.class.getResourceAsStream("pg_props2xml.xml"));

            quotesInValues        = "true".equals(xmlProps.getProperty("xml.do.quotes.in.values"));
            xmlQuote              = xmlProps.getProperty("xml.quote");
            lineFormat            = xmlProps.getProperty("xml.format.line");
            keyFormat             = xmlProps.getProperty("xml.format.key");
            doubleDash            = xmlProps.getProperty("xml.comment.double.dash");
            doubleDashReplacement = String.valueOf((char)Integer.parseInt(xmlProps.getProperty("xml.comment.dash.replacement", "8211"))).repeat(2);
            tab                   = " ".repeat(Math.max(0, Integer.parseInt(xmlProps.getProperty("xml.indent.width", "4"))));
            commentPattern        = Pattern.compile(xmlProps.getProperty("xml.comment.regexp"));

            for(String s : xmlProps.getProperty("xml.special.repl").split("\\s*,\\s*")) {
                String[] t = s.split("\\s*:\\s*", 2);
                if(t.length == 2) xmlChars.put(t[0], t[1]);
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int run(String... args) {
        if(args.length < 1) {
            System.err.println(msgs.getString("msg.err.p2x.no.props.file.specified"));
            return 1;
        }

        int errno = 0;

        for(String filename : args) {
            File fileIn  = new File(filename);
            File fileOut = new File(fileIn.getParentFile(), String.format("%s.xml", getNameWithoutExt(fileIn)));
            errno = convertFile(fileIn, fileOut);
        }

        return errno;
    }

    private int convertFile(File fileIn, File fileOut) {
        Properties vals = loadPropertyValues(fileIn);

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileIn), StandardCharsets.ISO_8859_1))) {
            String line = reader.readLine();

            if(line != null) {
                try(PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOut), StandardCharsets.UTF_8)))) {
                    writeMultipleLines(writer, "xml.header", "");

                    do {
                        Matcher m = commentPattern.matcher(line);

                        if(m.matches()) {
                            writeComment(writer, m.group(1));
                        }
                        else {
                            String[] z = line.split("\\s*=", 2);
                            if(z.length == 2) writeValue(writer, z[0].trim(), vals, quotesInValues);
                            else writeComment(writer, line.trim());
                        }

                        line = reader.readLine();
                    }
                    while(line != null);

                    writeMultipleLines(writer, "xml.footer", "");
                    writer.flush();
                }
            }

            return 0;
        }
        catch(Exception e) {
            System.err.printf(msgs.getString("msg.err.p2x.error"), e);
            return 1;
        }
    }

    private @NotNull String escapeSpecialChars(@NotNull String str, boolean doQuotes) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0, j = str.length(); i < j; i++) {
            char ch = str.charAt(i);
            if(ch == '"' && doQuotes) {
                sb.append(xmlQuote);
            }
            else if(ch < ' ') {
                sb.append(String.format("&#%d;", (int)ch));
            }
            else {
                String cs = ("" + ch);
                sb.append(Objects.toString(xmlChars.get(cs), cs));
            }
        }

        return sb.toString();
    }

    private @NotNull String getNameWithoutExt(File fileIn) {
        String fn = fileIn.getName();
        int    i  = fn.lastIndexOf('.');
        return ((i <= 0) ? fn : fn.substring(0, i));
    }

    private @NotNull Properties loadPropertyValues(File fileIn) {
        Properties vals = new Properties();
        try(BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(fileIn))) {
            vals.load(inputStream);
        }
        catch(Exception e) {
            System.err.printf(msgs.getString("msg.err.p2x.error"), e);
            System.exit(1);
        }
        return vals;
    }

    private void writeComment(@NotNull PrintWriter writer, @NotNull String str) {
        writer.printf(lineFormat, tab, String.format(xmlProps.getProperty("xml.comment.format"), str.replace(doubleDash, doubleDashReplacement)));
    }

    private void writeMultipleLines(@NotNull PrintWriter writer, @NotNull String pfx, @NotNull String tab) {
        for(int i = 1; i < 100; i++) {
            String s = xmlProps.getProperty(String.format(keyFormat, pfx, i));
            if(s == null) return;
            writer.printf(lineFormat, tab, s);
        }
    }

    private void writeValue(@NotNull PrintWriter writer, @NotNull String rawKey, @NotNull Properties vals, boolean doQuotesInValues) {
        String key = escapeSpecialChars(rawKey, true);
        String val = vals.getProperty(key);
        if(val == null) writeComment(writer, key);
        else writer.printf(lineFormat, tab, String.format(xmlProps.getProperty((val.length() == 0) ? "xml.entry.novalue" : "xml.entry.value"), key, escapeSpecialChars(val, doQuotesInValues)));
    }

    public static void main(String[] args) {
        try {
            System.exit(new PropertiesToXML().run(args));
        }
        catch(Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
