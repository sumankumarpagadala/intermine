package org.flymine.ontology;

/*
 * Copyright (C) 2002-2003 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.io.BufferedReader;
import java.util.StringTokenizer;


/**
 * Parse a file in DAG format into a tree of DagTerms.
 * This code borrows heavily from code written by Iwei Yeh of Stanford University.
 *
 * @author Richard Smith
 *
 */
public class DagParser
{

    protected Stack parents = new Stack();
    protected HashSet terms = new HashSet();
    protected HashMap seenTerms = new HashMap();

    private final String comment = "!";
    private final String domain = "$";
    private final String isa = "%";
    private final String partof = "<";
    private final String delimiter = " ; ";

    /**
     * Parse a DAG file to produce a set of toplevel DagTerms.
     * @param input text in DAG format
     * @return a set of DagTerms - will contain only toplevel (domain) terms
     * @throws Exception if anything goes wrong
     */
    public Set process(BufferedReader input) throws Exception {
        readTerms(input);
        return terms;
    }

    /**
     * Read DAG input line by line to generate hierarchy of DagTerms.
     * @param input text in DAG format
     * @throws Exception if anything goes wrong
     */
    public void readTerms(BufferedReader input) throws Exception {
        String line;
        int prevspaces = -1;
        int currspaces;

        while ((line = input.readLine()) != null) {
            DagTerm term = null;

            if (!line.startsWith(comment) && !line.equals("")) {
                int length = line.length();
                line = trimLeft(line);
                currspaces = length - line.length();
                if (prevspaces == -1) {
                    prevspaces = currspaces;
                    term = makeDagTerm(line);
                    parents.push(term);
                } else if (currspaces == prevspaces) {
                    // same parent as previous term
                    parents.pop();
                    term = makeDagTerm(line);
                    parents.push(term);
                } else if (currspaces > prevspaces) {
                    // term is a child of previous
                    term = makeDagTerm(line);
                    parents.push(term);
                } else if (currspaces < prevspaces) {
                    // how far have we moved back up nesting?
                    for (int i = currspaces; i <= prevspaces; i++) {
                        parents.pop();
                    }
                    term = makeDagTerm(line);
                    parents.push(term);
                }
                prevspaces = currspaces;
            }
        }
    }


    /**
     * Create (or get from already seen terms) a DagTerm given a line of DAG text.
     * Keeps track of indentation to manage hierachical relationships.
     * @param line a line of DAG text
     * @return a DagTerm create from line of text
     * @throws Exception if anything goes wrong
     */
    protected DagTerm makeDagTerm(String line) throws Exception {
        line = line.trim();
        String token = line.substring(0, 1);
        line = line.substring(1);
        StringTokenizer tokenizer = new StringTokenizer(line, (domain + isa + partof), true);

        String termStr = tokenizer.nextToken();
        DagTerm term = null;

        // details of this class from first token
        term = dagTermFromString(termStr);

        if (token.equals(domain)) {
            terms.add(term);
        } else if (token.equals(isa)) {
            DagTerm parent = (DagTerm) parents.peek();
            parent.addChild(term);
        } else if (token.equals(partof)) {
            DagTerm whole = (DagTerm) parents.peek();
            whole.addComponent(term);
        }

        // other tokens are additional relations; parents or partofs
        while (tokenizer.hasMoreTokens()) {
            String relation = tokenizer.nextToken();
            if (relation.equals(isa)) {
                relation = tokenizer.nextToken();
                DagTerm parent = dagTermFromString(relation.trim());
                parent.addChild(term);
            }  else if (relation.equals(partof)) {
                relation = tokenizer.nextToken();
                DagTerm whole = dagTermFromString(relation);
                whole.addComponent(term);
            }
        }
        return term;
    }


    /**
     * Create (or get from already seen terms) a DagTerm given a string defining the term.
     * @param details string representing a DAG term
     * @return the generated DagTerm
     * @throws Exception if cannot find a name and id in string
     */
    public DagTerm dagTermFromString(String details) throws Exception {
        details = details.trim();
        String[] elements = details.split(delimiter);
        String name = stripEscaped(elements[0]);
        String id = elements[1];
        if (elements.length < 2) {
            throw new Exception("term does not have and id");
        }

        // TODO check that 0 and 1 are name and id, handle broken terms better

        Identifier identifier = new Identifier(id, name);
        DagTerm term = (DagTerm) seenTerms.get(identifier);
        if (term == null) {
            term = new DagTerm(id, name);
            seenTerms.put(identifier, term);
        }

        // zero or more synonyms follow name and id
        for (int i = 2; i < elements.length; i++) {
            if (elements[i].startsWith("synonym:")) {
                term.addSynonym(elements[i].substring(elements[i].indexOf(":") + 1));
            }
        }
        return term;
    }

    /**
     * Number of spaces at start of line determines depth of term, String.trim() might
     * cause problems with trailing whitespace.
     * @param s string to process
     * @return a left-trimmed string
     **/
    protected String trimLeft(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return s.substring(i);
            }
        }
        return s;
    }


    /**
     * Some punctuation characters are escaped in DAG files, remove backslashes.
     * @param s string to remove escaped characters from
     * @return a cleaned-up string
     */
    protected String stripEscaped(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            if ('\\' != s.charAt(i) && !Character.isLetterOrDigit((char) (i + 1))) {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }


    /**
     * temporary main method to aid evaluation
     * @param args dagFile
     */
//     public static void main(String[] args) {
//         String dagFilename = args[0];
//         try {
//             File dagFile = new File(dagFilename);
//             DagParser parser = new DagParser();
//             FileReader reader = new FileReader(dagFile);
//             Set terms = parser.process(new BufferedReader(reader));
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }


    /**
     * Inner class to identify a DagTerm by its name and id.  If the same id has two different
     * names or vice versa both will be included in hieracrchy.
     */
    public class Identifier
    {
        protected String id;
        protected String name;

        /**
         * Construct with an id and name
         * @param id a term id
         * @param name a term name
         */
        public Identifier(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Test identifier for equality, Identifiers are equal if id and name are the same
         * @param o Obejct to test for equality with
         * @return true if equal
         */
        public boolean equals(Object o) {
            if (o instanceof Identifier) {
                Identifier i = (Identifier) o;
                return name.equals(i.name)
                    && id.equals(i.id);
            }
            return false;
        }

        /**
         * Generate a hashCode.
         * @return the hashCode
         */
        public int hashCode() {
            return 3 * name.hashCode() + 5 * id.hashCode();
        }
    }
}


