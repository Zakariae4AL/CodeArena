/** Starter code shown when a problem is opened for the first time. */
export const templates: Record<string, string> = {
  cpp: `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    long long a, b;
    cin >> a >> b;
    cout << a + b << "\\n";
    return 0;
}
`,
  java: `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(in.readLine());
        long a = Long.parseLong(st.nextToken());
        long b = Long.parseLong(st.nextToken());
        System.out.println(a + b);
    }
}
`,
  python: `import sys

def main():
    data = sys.stdin.read().split()
    a, b = int(data[0]), int(data[1])
    print(a + b)

main()
`,
  javascript: `const data = require('fs').readFileSync(0, 'utf8').trim().split(/\\s+/);
const a = BigInt(data[0]);
const b = BigInt(data[1]);
console.log((a + b).toString());
`,
};

/** Monaco language id for each judge language. */
export const monacoLanguage: Record<string, string> = {
  cpp: 'cpp',
  java: 'java',
  python: 'python',
  javascript: 'javascript',
};

const draftKey = (problemId: number, language: string) => `codearena.draft.${problemId}.${language}`;

export const drafts = {
  load(problemId: number, language: string): string {
    try {
      return localStorage.getItem(draftKey(problemId, language)) ?? templates[language] ?? '';
    } catch {
      return templates[language] ?? '';
    }
  },
  save(problemId: number, language: string, code: string) {
    try {
      localStorage.setItem(draftKey(problemId, language), code);
    } catch {
      /* ignore */
    }
  },
};
