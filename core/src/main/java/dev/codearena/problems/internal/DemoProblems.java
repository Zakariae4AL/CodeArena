package dev.codearena.problems.internal;

import dev.codearena.problems.Difficulty;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A small problem set for local runs. Large hidden tests are generated here (with a fixed
 * seed) and their answers computed with reference solutions, so slow solutions get TLE.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "codearena.seed.demo", havingValue = "true")
class DemoProblems implements ApplicationRunner {

    private final ProblemRepository problems;
    private final Clock clock;

    DemoProblems(ProblemRepository problems, Clock clock) {
        this.problems = problems;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (problems.count() > 0) {
            return;
        }
        aPlusB();
        balancedBrackets();
        reverseWords();
        countPrimes();
        pairsWithSum();
        longestIncreasingSubsequence();
    }

    private void save(String slug, String title, Difficulty difficulty, int timeLimitMs, String statement,
                      List<TestCase> tests) {
        Problem p = new Problem(slug, clock.instant());
        p.update(slug, title, statement.strip(), difficulty, timeLimitMs, 256, true);
        p.replaceTests(tests);
        problems.save(p);
    }

    private static List<TestCase> tests(String[][] cases, int samples) {
        List<TestCase> list = new ArrayList<>();
        for (int i = 0; i < cases.length; i++) {
            list.add(new TestCase(i + 1, cases[i][0], cases[i][1], i < samples));
        }
        return list;
    }

    private void aPlusB() {
        Random r = new Random(1);
        List<String[]> cases = new ArrayList<>(List.of(
                new String[] {"1 2\n", "3\n"}, new String[] {"-5 5\n", "0\n"},
                new String[] {"1000000000000 1000000000000\n", "2000000000000\n"}));
        for (int i = 0; i < 5; i++) {
            long a = r.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            long b = r.nextLong(-1_000_000_000_000L, 1_000_000_000_000L);
            cases.add(new String[] {a + " " + b + "\n", (a + b) + "\n"});
        }
        save("a-plus-b", "A + B", Difficulty.EASY, 1000, """
                Read two integers **a** and **b** and print their sum.

                ### Input
                One line with two integers `a` and `b` (−10¹² ≤ a, b ≤ 10¹²).

                ### Output
                Print `a + b`.

                > Tip: the numbers do not fit in 32 bits — use `long long`, `long` or a big integer.
                """, tests(cases.toArray(String[][]::new), 1));
    }

    private void balancedBrackets() {
        String[] lines = {"([]{})", "([)]", "((", "", "{[()()]}", "]"};
        StringBuilder in = new StringBuilder().append(lines.length).append('\n');
        StringBuilder out = new StringBuilder();
        for (String l : lines) {
            in.append(l).append('\n');
            out.append(balanced(l) ? "YES" : "NO").append('\n');
        }
        Random r = new Random(2);
        StringBuilder bigIn = new StringBuilder("200\n");
        StringBuilder bigOut = new StringBuilder();
        for (int t = 0; t < 200; t++) {
            String s = randomBrackets(r, 1 + r.nextInt(2000));
            bigIn.append(s).append('\n');
            bigOut.append(balanced(s) ? "YES" : "NO").append('\n');
        }
        save("balanced-brackets", "Balanced Brackets", Difficulty.EASY, 1000, """
                A string of brackets `()[]{}` is **balanced** if every opening bracket is closed
                by the same type of bracket, in the right order.

                ### Input
                The first line contains `T` (1 ≤ T ≤ 200). Each of the next `T` lines contains a string
                of at most 2000 brackets (it may be empty).

                ### Output
                For each string print `YES` if it is balanced, otherwise `NO`.
                """, tests(new String[][] {{in.toString(), out.toString()}, {bigIn.toString(), bigOut.toString()}}, 1));
    }

    private static String randomBrackets(Random r, int pairs) {
        if (r.nextBoolean()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pairs; i++) {
                sb.append("()[]{}".charAt(r.nextInt(6)));
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        for (int i = 0; i < pairs; i++) {
            if (!stack.isEmpty() && r.nextBoolean()) {
                sb.append(stack.pop());
            } else {
                int k = r.nextInt(3);
                sb.append("([{".charAt(k));
                stack.push(")]}".charAt(k));
            }
        }
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }
        return sb.toString();
    }

    private static boolean balanced(String s) {
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(' -> stack.push(')');
                case '[' -> stack.push(']');
                case '{' -> stack.push('}');
                default -> {
                    if (stack.isEmpty() || stack.pop() != c) {
                        return false;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    private void reverseWords() {
        String[] words = "the quick brown fox jumps over the lazy dog".split(" ");
        Random r = new Random(3);
        List<String> big = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            big.add(words[r.nextInt(words.length)]);
        }
        List<String> reversed = new ArrayList<>(big);
        Collections.reverse(reversed);
        save("reverse-words", "Reverse the Words", Difficulty.EASY, 1000, """
                Print the words of a sentence in reverse order.

                ### Input
                One line of at most 10⁶ characters: words of lowercase letters separated by single spaces.

                ### Output
                The same words in reverse order, separated by single spaces.
                """, tests(new String[][] {
                {"hello world\n", "world hello\n"},
                {"codearena\n", "codearena\n"},
                {String.join(" ", big) + "\n", String.join(" ", reversed) + "\n"}}, 1));
    }

    private void countPrimes() {
        int[] ns = {10, 1, 2, 100, 1_000_000, 10_000_000};
        boolean[] composite = new boolean[10_000_001];
        composite[0] = composite[1] = true;
        for (int i = 2; (long) i * i <= 10_000_000; i++) {
            if (!composite[i]) {
                for (int j = i * i; j <= 10_000_000; j += i) {
                    composite[j] = true;
                }
            }
        }
        int[] prefix = new int[10_000_001];
        for (int i = 1; i <= 10_000_000; i++) {
            prefix[i] = prefix[i - 1] + (composite[i] ? 0 : 1);
        }
        String[][] cases = Arrays.stream(ns).mapToObj(n -> new String[] {n + "\n", prefix[n] + "\n"}).toArray(String[][]::new);
        save("count-primes", "Count the Primes", Difficulty.MEDIUM, 1000, """
                How many prime numbers are there between 1 and **N**?

                ### Input
                One integer `N` (1 ≤ N ≤ 10⁷).

                ### Output
                The number of primes `p` with `p ≤ N`.

                > Checking every number one by one is too slow for N = 10⁷. Think about the Sieve of Eratosthenes.
                """, tests(cases, 1));
    }

    private void pairsWithSum() {
        List<String[]> cases = new ArrayList<>();
        cases.add(new String[] {"5 6\n1 5 3 3 4\n", "2\n"});
        cases.add(new String[] {"4 2\n1 1 1 1\n", "6\n"});
        Random r = new Random(4);
        for (int n : new int[] {1000, 200_000}) {
            long[] a = new long[n];
            for (int i = 0; i < n; i++) {
                a[i] = r.nextInt(1, 100_000);
            }
            long k = 100_000;
            Map<Long, Long> seen = new HashMap<>();
            long count = 0;
            StringJoiner line = new StringJoiner(" ");
            for (long x : a) {
                count += seen.getOrDefault(k - x, 0L);
                seen.merge(x, 1L, Long::sum);
                line.add(Long.toString(x));
            }
            cases.add(new String[] {n + " " + k + "\n" + line + "\n", count + "\n"});
        }
        save("pairs-with-sum", "Pairs with a Given Sum", Difficulty.MEDIUM, 1000, """
                Count the pairs of positions `i < j` with `a[i] + a[j] = K`.

                ### Input
                The first line contains `N` and `K` (1 ≤ N ≤ 2·10⁵, 1 ≤ K ≤ 2·10⁹).
                The second line contains `N` integers `a[i]` (1 ≤ a[i] ≤ 10⁹).

                ### Output
                The number of pairs. It can exceed 32 bits.

                ### Example
                For `1 5 3 3 4` and `K = 6`, the pairs are (1, 5) and (3, 3).
                """, tests(cases.toArray(String[][]::new), 2));
    }

    private void longestIncreasingSubsequence() {
        List<String[]> cases = new ArrayList<>();
        cases.add(new String[] {"8\n10 9 2 5 3 7 101 18\n", "4\n"});
        cases.add(new String[] {"5\n5 4 3 2 1\n", "1\n"});
        Random r = new Random(5);
        for (int n : new int[] {2000, 200_000}) {
            int[] a = new int[n];
            StringJoiner line = new StringJoiner(" ");
            for (int i = 0; i < n; i++) {
                a[i] = r.nextInt(1_000_000_000);
                line.add(Integer.toString(a[i]));
            }
            cases.add(new String[] {n + "\n" + line + "\n", lis(a) + "\n"});
        }
        save("longest-increasing-subsequence", "Longest Increasing Subsequence", Difficulty.HARD, 1500, """
                Find the length of the longest **strictly increasing** subsequence of an array.
                A subsequence keeps the original order but may skip elements.

                ### Input
                The first line contains `N` (1 ≤ N ≤ 2·10⁵), the second line `N` integers (0 ≤ a[i] < 10⁹).

                ### Output
                The length of the longest strictly increasing subsequence.

                > An O(N²) solution is too slow for the largest test: aim for O(N log N).
                """, tests(cases.toArray(String[][]::new), 1));
    }

    private static int lis(int[] a) {
        int[] tails = new int[a.length];
        int size = 0;
        for (int x : a) {
            int pos = Arrays.binarySearch(tails, 0, size, x);
            if (pos < 0) {
                pos = -pos - 1;
            }
            tails[pos] = x;
            if (pos == size) {
                size++;
            }
        }
        return size;
    }
}
