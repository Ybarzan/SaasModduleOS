import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Award, Clock, BarChart3, Users, GraduationCap, ArrowLeft, CheckCircle, XCircle, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface AcademyModule {
  id: string;
  title: string;
  description: string;
  content?: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  category: string;
  durationHours: number;
  status?: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'PASSED' | 'FAILED';
  progress?: number;
  enrollmentId?: string;
  quiz?: QuizQuestion[];
}

interface QuizQuestion {
  id: string;
  question: string;
  options: string[];
  correctAnswer?: string;
}

interface DashboardStats {
  totalModules: number;
  enrolled: number;
  completed: number;
  passRate: number;
}

const difficultyColors: Record<string, string> = {
  BEGINNER: 'bg-success/10 text-success',
  INTERMEDIATE: 'bg-accent-soft text-accent-strong',
  ADVANCED: 'bg-warning/10 text-warning',
  EXPERT: 'bg-danger/10 text-danger',
};

const statusIcons: Record<string, typeof CheckCircle> = {
  COMPLETED: CheckCircle,
  PASSED: Award,
  FAILED: XCircle,
  IN_PROGRESS: Clock,
};

const statusColors: Record<string, string> = {
  COMPLETED: 'text-success',
  PASSED: 'text-accent',
  FAILED: 'text-danger',
  IN_PROGRESS: 'text-warning',
};

const Academy = () => {
  const queryClient = useQueryClient();
  const [selectedModule, setSelectedModule] = useState<AcademyModule | null>(null);
  const [quizAnswers, setQuizAnswers] = useState<Record<string, string>>({});

  const { data: dashboardStats } = useQuery({
    queryKey: ['academy-dashboard'],
    queryFn: async () => {
      const res = await incokalkAPI.academy.dashboard();
      return res.data as DashboardStats;
    },
  });

  const { data: modulesData, isLoading: modulesLoading } = useQuery({
    queryKey: ['academy-modules'],
    queryFn: async () => {
      const res = await incokalkAPI.academy.modules();
      return res.data as AcademyModule[];
    },
  });

  const { data: moduleDetail } = useQuery({
    queryKey: ['academy-module', selectedModule?.id],
    queryFn: async () => {
      if (!selectedModule?.id) return null;
      const res = await incokalkAPI.academy.module(selectedModule.id);
      return res.data as AcademyModule;
    },
    enabled: !!selectedModule?.id,
  });

  const enrollMutation = useMutation({
    mutationFn: (moduleId: string) => incokalkAPI.academy.enroll(moduleId),
    onSuccess: () => {
      toast.success('Inscription réussie');
      queryClient.invalidateQueries({ queryKey: ['academy-modules'] });
      queryClient.invalidateQueries({ queryKey: ['academy-dashboard'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'inscription");
    },
  });

  const submitQuizMutation = useMutation({
    mutationFn: (data: { moduleId: string; answers: Record<string, string> }) =>
      incokalkAPI.academy.submitQuiz(data.moduleId, { answers: data.answers }),
    onSuccess: () => {
      toast.success('Quiz soumis avec succès');
      queryClient.invalidateQueries({ queryKey: ['academy-module', selectedModule?.id] });
      queryClient.invalidateQueries({ queryKey: ['academy-modules'] });
      queryClient.invalidateQueries({ queryKey: ['academy-dashboard'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la soumission du quiz');
    },
  });

  const modules: AcademyModule[] = Array.isArray(modulesData) ? modulesData : [];

  const handleEnroll = (moduleId: string) => {
    enrollMutation.mutate(moduleId);
  };

  const handleSubmitQuiz = () => {
    if (!selectedModule) return;
    submitQuizMutation.mutate({ moduleId: selectedModule.id, answers: quizAnswers });
  };

  const handleQuizAnswer = (questionId: string, answer: string) => {
    setQuizAnswers((prev) => ({ ...prev, [questionId]: answer }));
  };

  const handleViewModule = (mod: AcademyModule) => {
    setSelectedModule(mod);
    setQuizAnswers({});
  };

  const handleBack = () => {
    setSelectedModule(null);
    setQuizAnswers({});
  };

  const handleDownloadCertificate = async (enrollmentId: string) => {
    try {
      const res = await incokalkAPI.academy.certificate(enrollmentId);
      const blob = new Blob([res.data], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `certificat-${enrollmentId}.pdf`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Erreur lors du téléchargement du certificat');
    }
  };

  if (selectedModule && moduleDetail) {
    const quiz = moduleDetail.quiz || [];
    const hasQuiz = quiz.length > 0;
    const allAnswered = quiz.every((q) => quizAnswers[q.id]);
    const isPassed = moduleDetail.status === 'PASSED';

    return (
      <div className="max-w-4xl mx-auto px-4 py-8">
        <button
          onClick={handleBack}
          className="flex items-center gap-2 text-ink-soft hover:text-ink mb-6 transition-colors"
        >
          <ArrowLeft size={18} />
          Retour aux modules
        </button>

        <div className="relative bg-surface rounded-none border border-line p-6 mb-6">
          <span className="hud-corner hud-corner-tl" aria-hidden="true" />
          <span className="hud-corner hud-corner-tr" aria-hidden="true" />
          <span className="hud-corner hud-corner-bl" aria-hidden="true" />
          <span className="hud-corner hud-corner-br" aria-hidden="true" />
          <div className="flex items-start justify-between mb-4">
            <div>
              <h1 className="text-2xl font-bold text-ink">
                <span className="text-accent font-normal" aria-hidden="true">:: </span>
                {moduleDetail.title}
              </h1>
              <div className="flex items-center gap-3 mt-2">
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${difficultyColors[moduleDetail.difficulty]}`}>
                  {moduleDetail.difficulty}
                </span>
                <span className="text-sm text-ink-soft">{moduleDetail.category}</span>
                <span className="flex items-center gap-1 text-sm text-ink-soft">
                  <Clock size={14} />
                  {moduleDetail.durationHours}h
                </span>
              </div>
            </div>
            {moduleDetail.status && (
              <div className={`flex items-center gap-2 ${statusColors[moduleDetail.status] || 'text-ink-soft'}`}>
                {(() => {
                  const Icon = statusIcons[moduleDetail.status!] || Clock;
                  return <Icon size={20} />;
                })()}
                <span className="text-sm font-medium">{moduleDetail.status.replace('_', ' ')}</span>
              </div>
            )}
          </div>

          {moduleDetail.progress !== undefined && (
            <div className="mb-4">
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm text-ink-soft">Progression</span>
                <span className="text-sm font-medium text-ink-soft">{Math.round(moduleDetail.progress)}%</span>
              </div>
              <div className="w-full bg-surface-2 rounded-full h-2.5">
                <div
                  className="bg-success h-2.5 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(moduleDetail.progress, 100)}%` }}
                />
              </div>
            </div>
          )}

          {moduleDetail.content && (
            <div className="prose prose-sm max-w-none text-ink-soft">
              <div dangerouslySetInnerHTML={{ __html: moduleDetail.content }} />
            </div>
          )}

          {isPassed && moduleDetail.enrollmentId && (
            <div className="mt-6 p-4 bg-accent-soft rounded-none border border-accent/30">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Award size={20} className="text-accent" />
                  <span className="font-medium text-accent-strong">Module validé !</span>
                </div>
                <button
                  onClick={() => handleDownloadCertificate(moduleDetail.enrollmentId!)}
                  className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none text-sm font-medium hover:bg-accent-strong transition-colors"
                >
                  <GraduationCap size={16} />
                  Télécharger le certificat
                </button>
              </div>
            </div>
          )}
        </div>

        {hasQuiz && (
          <div className="bg-surface rounded-none border border-line p-6">
            <h2 className="text-lg font-semibold text-ink mb-4">Quiz</h2>
            <div className="space-y-6">
              {quiz.map((q, i) => (
                <div key={q.id}>
                  <p className="font-medium text-ink mb-3">
                    {i + 1}. {q.question}
                  </p>
                  <div className="space-y-2">
                    {q.options.map((opt) => (
                      <label
                        key={opt}
                        className={`flex items-center gap-3 p-3 rounded-none border cursor-pointer transition-colors ${
                          quizAnswers[q.id] === opt
                            ? 'border-accent bg-accent-soft'
                            : 'border-line hover:border-ink-soft'
                        }`}
                      >
                        <input
                          type="radio"
                          name={q.id}
                          value={opt}
                          checked={quizAnswers[q.id] === opt}
                          onChange={() => handleQuizAnswer(q.id, opt)}
                          className="text-accent"
                        />
                        <span className="text-sm text-ink-soft">{opt}</span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <button
              onClick={handleSubmitQuiz}
              disabled={!allAnswered || submitQuizMutation.isPending}
              className="mt-6 flex items-center gap-2 bg-success text-white px-6 py-2.5 rounded-none font-medium hover:bg-success/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {submitQuizMutation.isPending && <Loader2 size={16} className="animate-spin" />}
              Soumettre le quiz
            </button>
          </div>
        )}
      </div>
    );
  }

  const stats = dashboardStats;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          IncoKalk Academy
        </h1>
        <p className="text-ink-soft mt-1">Formation Incoterms 2020 & Trade Compliance</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-warning/10 flex items-center justify-center">
              <BookOpen size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Modules total</p>
              <p className="text-2xl font-bold text-ink">{stats?.totalModules ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <Users size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Inscrits</p>
              <p className="text-2xl font-bold text-ink">{stats?.enrolled ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
              <CheckCircle size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Terminés</p>
              <p className="text-2xl font-bold text-ink">{stats?.completed ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent/10 flex items-center justify-center">
              <BarChart3 size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Taux de réussite</p>
              <p className="text-2xl font-bold text-ink">{stats?.passRate != null ? `${stats.passRate}%` : '—'}</p>
            </div>
          </div>
        </div>
      </div>

      <h2 className="text-lg font-semibold text-ink mb-4">Modules de formation</h2>

      {modulesLoading ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : modules.length === 0 ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <BookOpen size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucun module disponible</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {modules.map((mod) => (
            <div
              key={mod.id}
              className="bg-surface rounded-none border border-line p-5 hover:shadow-md transition-shadow cursor-pointer flex flex-col"
              onClick={() => handleViewModule(mod)}
            >
              <div className="flex items-start justify-between mb-3">
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${difficultyColors[mod.difficulty]}`}>
                  {mod.difficulty}
                </span>
                <span className="text-xs text-ink-soft">{mod.category}</span>
              </div>
              <h3 className="font-semibold text-ink mb-2">{mod.title}</h3>
              <p className="text-sm text-ink-soft mb-4 flex-1">
                {mod.description.length > 120
                  ? `${mod.description.substring(0, 120)}...`
                  : mod.description}
              </p>
              <div className="flex items-center justify-between mt-auto pt-3 border-t border-line">
                <span className="flex items-center gap-1 text-sm text-ink-soft">
                  <Clock size={14} />
                  {mod.durationHours}h
                </span>
                {(mod.status === 'NOT_STARTED' || !mod.status) && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleEnroll(mod.id);
                    }}
                    disabled={enrollMutation.isPending}
                    className="text-sm font-medium text-accent hover:text-accent-strong disabled:opacity-50 transition-colors"
                  >
                    {enrollMutation.isPending ? '...' : "S'inscrire"}
                  </button>
                )}
                {mod.status === 'IN_PROGRESS' && (
                  <span className="flex items-center gap-1 text-sm text-warning font-medium">
                    <Clock size={14} />
                    En cours
                  </span>
                )}
                {(mod.status === 'COMPLETED' || mod.status === 'PASSED') && (
                  <span className="flex items-center gap-1 text-sm text-success font-medium">
                    <CheckCircle size={14} />
                    Terminé
                  </span>
                )}
                {mod.status === 'FAILED' && (
                  <span className="flex items-center gap-1 text-sm text-danger font-medium">
                    <XCircle size={14} />
                    Échoué
                  </span>
                )}
                {mod.progress !== undefined && mod.progress > 0 && mod.progress < 100 && (
                  <span className="text-xs text-ink-soft">{Math.round(mod.progress)}%</span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Academy;
