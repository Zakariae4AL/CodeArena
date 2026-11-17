import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

const en = {
  'nav.problems': 'Problems',
  'nav.contests': 'Contests',
  'nav.submissions': 'My submissions',
  'nav.admin': 'Admin',
  'nav.login': 'Sign in',
  'nav.register': 'Create account',
  'nav.logout': 'Sign out',
  'nav.theme': 'Switch theme',
  'nav.lang': 'FR',

  'home.badge': 'Open-source online judge',
  'home.title': 'Write code. Get judged in seconds.',
  'home.subtitle':
    'Solve algorithmic problems in C++, Java, Python or JavaScript. Every submission runs in its own locked-down sandbox and the verdict streams back test by test.',
  'home.cta': 'Start solving',
  'home.contests': 'See contests',
  'home.live': 'Live now',
  'home.how': 'How it works',
  'home.step1': 'Pick a problem and write your solution in the editor.',
  'home.step2': 'Run it on the examples, then submit it to the hidden tests.',
  'home.step3': 'Watch each test turn green — or find out exactly where it failed.',

  'problems.title': 'Problems',
  'problems.subtitle': 'Classic algorithmic problems, from warm-ups to sieves and binary searches.',
  'problems.solved': 'Solved',
  'problems.search': 'Search problems',
  'problems.empty': 'No problem matches your search.',
  'difficulty.EASY': 'Easy',
  'difficulty.MEDIUM': 'Medium',
  'difficulty.HARD': 'Hard',

  'ws.statement': 'Statement',
  'ws.submissions': 'Submissions',
  'ws.limits': '{time} s · {memory} MB',
  'ws.example': 'Example {n}',
  'ws.input': 'Input',
  'ws.output': 'Output',
  'ws.expected': 'Expected',
  'ws.stderr': 'Standard error',
  'ws.run': 'Run examples',
  'ws.submit': 'Submit',
  'ws.signin': 'Sign in to run your code',
  'ws.reset': 'Reset code',
  'ws.results': 'Results',
  'ws.idle': 'Run your code on the examples, or submit it to the hidden tests.',
  'ws.queued': 'Waiting for a judge…',
  'ws.running': 'Judging test {done} of {total}…',
  'ws.test': 'Test {n}',
  'ws.compileError': 'Compilation error',
  'ws.passed': '{passed} / {total} tests passed',
  'ws.contestPractice': 'This contest has ended: submissions are practice only.',
  'ws.none': 'No submissions yet.',

  'verdict.ACCEPTED': 'Accepted',
  'verdict.WRONG_ANSWER': 'Wrong answer',
  'verdict.TIME_LIMIT_EXCEEDED': 'Time limit exceeded',
  'verdict.MEMORY_LIMIT_EXCEEDED': 'Memory limit exceeded',
  'verdict.OUTPUT_LIMIT_EXCEEDED': 'Output limit exceeded',
  'verdict.RUNTIME_ERROR': 'Runtime error',
  'verdict.COMPILATION_ERROR': 'Compilation error',
  'verdict.SYSTEM_ERROR': 'System error',
  'status.QUEUED': 'In queue',
  'status.RUNNING': 'Running',

  'contests.title': 'Contests',
  'contests.subtitle': 'Timed rounds with a live ICPC scoreboard.',
  'contests.empty': 'No contests yet.',
  'phase.UPCOMING': 'Upcoming',
  'phase.RUNNING': 'Running',
  'phase.ENDED': 'Ended',
  'contest.startsIn': 'Starts in',
  'contest.endsIn': 'Ends in',
  'contest.ended': 'Ended',
  'contest.problems': 'Problems',
  'contest.problemCount': '{n} problems',
  'contest.scoreboard': 'Scoreboard',
  'contest.hidden': 'The problems will appear when the contest starts.',
  'contest.rules': 'ICPC rules: rank by problems solved, then by penalty (minutes to solve + 20 per wrong try).',
  'board.rank': '#',
  'board.user': 'Contestant',
  'board.solved': 'Solved',
  'board.penalty': 'Penalty',
  'board.empty': 'No accepted or failed submissions yet.',
  'board.live': 'Live',

  'subs.title': 'My submissions',
  'subs.problem': 'Problem',
  'subs.language': 'Language',
  'subs.verdict': 'Verdict',
  'subs.time': 'Time',
  'subs.memory': 'Memory',
  'subs.when': 'When',
  'subs.empty': 'You have not submitted anything yet.',
  'subs.detail': 'Submission',
  'subs.code': 'Code',
  'subs.tests': 'Tests',

  'auth.loginTitle': 'Welcome back',
  'auth.registerTitle': 'Join the arena',
  'auth.username': 'Username',
  'auth.email': 'Email',
  'auth.password': 'Password',
  'auth.passwordHint': 'At least 8 characters with a letter and a digit.',
  'auth.login': 'Sign in',
  'auth.register': 'Create account',
  'auth.noAccount': 'New here?',
  'auth.hasAccount': 'Already registered?',
  'auth.demo': 'Demo: {admin} or {user}, password {password}',

  'admin.title': 'Admin',
  'admin.problems': 'Problems',
  'admin.contests': 'Contests',
  'admin.newProblem': 'New problem',
  'admin.newContest': 'New contest',
  'admin.draft': 'Draft',
  'admin.published': 'Published',
  'admin.tests': '{n} tests',
  'admin.edit': 'Edit',
  'admin.save': 'Save',
  'admin.saved': 'Saved.',
  'admin.slug': 'Slug',
  'admin.titleField': 'Title',
  'admin.statement': 'Statement (Markdown)',
  'admin.difficulty': 'Difficulty',
  'admin.timeLimit': 'Time limit (ms)',
  'admin.memoryLimit': 'Memory limit (MB)',
  'admin.publish': 'Published in the library',
  'admin.testCases': 'Test cases',
  'admin.addTest': 'Add a test',
  'admin.sample': 'Sample (visible to everyone)',
  'admin.remove': 'Remove',
  'admin.description': 'Description',
  'admin.startsAt': 'Starts at',
  'admin.endsAt': 'Ends at',
  'admin.pickProblems': 'Problems (in order: A, B, C…)',

  'common.loading': 'Loading…',
  'common.error': 'Something went wrong.',
  'common.back': 'Back',
  'notFound.title': 'Nothing here',
  'notFound.back': 'Back to the problems',
} as const;

export type Key = keyof typeof en;

const fr: Record<Key, string> = {
  'nav.problems': 'Problèmes',
  'nav.contests': 'Concours',
  'nav.submissions': 'Mes soumissions',
  'nav.admin': 'Admin',
  'nav.login': 'Se connecter',
  'nav.register': 'Créer un compte',
  'nav.logout': 'Se déconnecter',
  'nav.theme': 'Changer de thème',
  'nav.lang': 'EN',

  'home.badge': 'Juge en ligne open source',
  'home.title': 'Écrivez du code. Soyez jugé en quelques secondes.',
  'home.subtitle':
    'Résolvez des problèmes d’algorithmique en C++, Java, Python ou JavaScript. Chaque soumission tourne dans son propre bac à sable verrouillé et le verdict arrive test par test.',
  'home.cta': 'Commencer',
  'home.contests': 'Voir les concours',
  'home.live': 'En direct',
  'home.how': 'Comment ça marche',
  'home.step1': 'Choisissez un problème et écrivez votre solution dans l’éditeur.',
  'home.step2': 'Testez-la sur les exemples, puis soumettez-la aux tests cachés.',
  'home.step3': 'Regardez chaque test passer au vert — ou voyez exactement où il échoue.',

  'problems.title': 'Problèmes',
  'problems.subtitle':
    'Des problèmes d’algorithmique classiques, de l’échauffement aux cribles et recherches dichotomiques.',
  'problems.solved': 'Résolu',
  'problems.search': 'Rechercher un problème',
  'problems.empty': 'Aucun problème ne correspond.',
  'difficulty.EASY': 'Facile',
  'difficulty.MEDIUM': 'Moyen',
  'difficulty.HARD': 'Difficile',

  'ws.statement': 'Énoncé',
  'ws.submissions': 'Soumissions',
  'ws.limits': '{time} s · {memory} Mo',
  'ws.example': 'Exemple {n}',
  'ws.input': 'Entrée',
  'ws.output': 'Sortie',
  'ws.expected': 'Attendu',
  'ws.stderr': 'Erreur standard',
  'ws.run': 'Tester les exemples',
  'ws.submit': 'Soumettre',
  'ws.signin': 'Connectez-vous pour exécuter votre code',
  'ws.reset': 'Réinitialiser',
  'ws.results': 'Résultats',
  'ws.idle': 'Testez votre code sur les exemples, ou soumettez-le aux tests cachés.',
  'ws.queued': 'En attente d’un juge…',
  'ws.running': 'Test {done} sur {total}…',
  'ws.test': 'Test {n}',
  'ws.compileError': 'Erreur de compilation',
  'ws.passed': '{passed} / {total} tests réussis',
  'ws.contestPractice': 'Ce concours est terminé : les soumissions comptent comme entraînement.',
  'ws.none': 'Aucune soumission pour le moment.',

  'verdict.ACCEPTED': 'Accepté',
  'verdict.WRONG_ANSWER': 'Mauvaise réponse',
  'verdict.TIME_LIMIT_EXCEEDED': 'Temps dépassé',
  'verdict.MEMORY_LIMIT_EXCEEDED': 'Mémoire dépassée',
  'verdict.OUTPUT_LIMIT_EXCEEDED': 'Sortie trop longue',
  'verdict.RUNTIME_ERROR': 'Erreur d’exécution',
  'verdict.COMPILATION_ERROR': 'Erreur de compilation',
  'verdict.SYSTEM_ERROR': 'Erreur système',
  'status.QUEUED': 'En file',
  'status.RUNNING': 'En cours',

  'contests.title': 'Concours',
  'contests.subtitle': 'Des épreuves chronométrées avec un classement ICPC en direct.',
  'contests.empty': 'Aucun concours pour le moment.',
  'phase.UPCOMING': 'À venir',
  'phase.RUNNING': 'En cours',
  'phase.ENDED': 'Terminé',
  'contest.startsIn': 'Commence dans',
  'contest.endsIn': 'Se termine dans',
  'contest.ended': 'Terminé',
  'contest.problems': 'Problèmes',
  'contest.problemCount': '{n} problèmes',
  'contest.scoreboard': 'Classement',
  'contest.hidden': 'Les problèmes apparaîtront au début du concours.',
  'contest.rules': 'Règles ICPC : classement par problèmes résolus, puis par pénalité (minutes + 20 par essai raté).',
  'board.rank': '#',
  'board.user': 'Participant',
  'board.solved': 'Résolus',
  'board.penalty': 'Pénalité',
  'board.empty': 'Aucune soumission jugée pour le moment.',
  'board.live': 'Direct',

  'subs.title': 'Mes soumissions',
  'subs.problem': 'Problème',
  'subs.language': 'Langage',
  'subs.verdict': 'Verdict',
  'subs.time': 'Temps',
  'subs.memory': 'Mémoire',
  'subs.when': 'Date',
  'subs.empty': 'Vous n’avez encore rien soumis.',
  'subs.detail': 'Soumission',
  'subs.code': 'Code',
  'subs.tests': 'Tests',

  'auth.loginTitle': 'Bon retour',
  'auth.registerTitle': 'Rejoindre l’arène',
  'auth.username': 'Nom d’utilisateur',
  'auth.email': 'E-mail',
  'auth.password': 'Mot de passe',
  'auth.passwordHint': 'Au moins 8 caractères avec une lettre et un chiffre.',
  'auth.login': 'Se connecter',
  'auth.register': 'Créer le compte',
  'auth.noAccount': 'Nouveau ici ?',
  'auth.hasAccount': 'Déjà inscrit ?',
  'auth.demo': 'Démo : {admin} ou {user}, mot de passe {password}',

  'admin.title': 'Administration',
  'admin.problems': 'Problèmes',
  'admin.contests': 'Concours',
  'admin.newProblem': 'Nouveau problème',
  'admin.newContest': 'Nouveau concours',
  'admin.draft': 'Brouillon',
  'admin.published': 'Publié',
  'admin.tests': '{n} tests',
  'admin.edit': 'Modifier',
  'admin.save': 'Enregistrer',
  'admin.saved': 'Enregistré.',
  'admin.slug': 'Identifiant',
  'admin.titleField': 'Titre',
  'admin.statement': 'Énoncé (Markdown)',
  'admin.difficulty': 'Difficulté',
  'admin.timeLimit': 'Limite de temps (ms)',
  'admin.memoryLimit': 'Limite de mémoire (Mo)',
  'admin.publish': 'Publié dans la bibliothèque',
  'admin.testCases': 'Cas de test',
  'admin.addTest': 'Ajouter un test',
  'admin.sample': 'Exemple (visible par tous)',
  'admin.remove': 'Supprimer',
  'admin.description': 'Description',
  'admin.startsAt': 'Début',
  'admin.endsAt': 'Fin',
  'admin.pickProblems': 'Problèmes (dans l’ordre : A, B, C…)',

  'common.loading': 'Chargement…',
  'common.error': 'Une erreur est survenue.',
  'common.back': 'Retour',
  'notFound.title': 'Rien ici',
  'notFound.back': 'Retour aux problèmes',
};

export type Lang = 'en' | 'fr';
const dictionaries: Record<Lang, Record<Key, string>> = { en, fr };

interface I18n {
  lang: Lang;
  locale: string;
  t: (key: Key | string, params?: Record<string, string | number>) => string;
  toggle: () => void;
}

const I18nContext = createContext<I18n | null>(null);

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem('codearena.lang');
    if (saved === 'en' || saved === 'fr') return saved;
  } catch {
    /* ignore */
  }
  return typeof navigator !== 'undefined' && navigator.language?.startsWith('fr') ? 'fr' : 'en';
}

export function I18nProvider({ children, lang: forced }: { children: ReactNode; lang?: Lang }) {
  const [lang, setLang] = useState<Lang>(forced ?? initialLang);

  useEffect(() => {
    document.documentElement.lang = lang;
    try {
      localStorage.setItem('codearena.lang', lang);
    } catch {
      /* ignore */
    }
  }, [lang]);

  const t = useCallback(
    (key: Key | string, params: Record<string, string | number> = {}) => {
      const text = dictionaries[lang][key as Key] ?? key;
      return text.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? `{${name}}`));
    },
    [lang],
  );

  const value = useMemo<I18n>(
    () => ({
      lang,
      locale: lang === 'fr' ? 'fr-FR' : 'en-GB',
      t,
      toggle: () => setLang((l) => (l === 'en' ? 'fr' : 'en')),
    }),
    [lang, t],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside I18nProvider');
  return ctx;
}

export const translations = dictionaries;
