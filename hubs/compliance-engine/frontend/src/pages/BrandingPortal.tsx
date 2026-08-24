import type { AxiosError } from 'axios';
import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Palette, Image, Globe, Type, Eye, Save, RefreshCw, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface BrandingSettings {
  logoUrl?: string;
  primaryColor: string;
  secondaryColor: string;
  portalTitle: string;
  welcomeMessage: string;
  footerText: string;
  customDomain?: string;
  customCssEnabled: boolean;
  customCss?: string;
}

const DEFAULT_BRANDING: BrandingSettings = {
  primaryColor: '#7c3aed',
  secondaryColor: '#f59e0b',
  portalTitle: 'Portail Client',
  welcomeMessage: 'Bienvenue sur votre portail de suivi de livraisons',
  footerText: '© 2026 IncoKalk - Tous droits réservés',
  customCssEnabled: false,
  customCss: '',
};

const BrandingPortal = () => {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [settings, setSettings] = useState<BrandingSettings>(DEFAULT_BRANDING);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);
  const [hasChanges, setHasChanges] = useState(false);

  const { data: brandingData, isLoading } = useQuery({
    queryKey: ['branding'],
    queryFn: async () => {
      const res = await incokalkAPI.branding.get();
      return res.data;
    },
  });

  const [prevBranding, setPrevBranding] = useState(brandingData);
  if (brandingData !== prevBranding) {
    setPrevBranding(brandingData);
    if (brandingData) {
      setSettings({
        primaryColor: brandingData.primaryColor || DEFAULT_BRANDING.primaryColor,
        secondaryColor: brandingData.secondaryColor || DEFAULT_BRANDING.secondaryColor,
        portalTitle: brandingData.portalTitle || DEFAULT_BRANDING.portalTitle,
        welcomeMessage: brandingData.welcomeMessage || DEFAULT_BRANDING.welcomeMessage,
        footerText: brandingData.footerText || DEFAULT_BRANDING.footerText,
        customDomain: brandingData.customDomain || '',
        customCssEnabled: brandingData.customCssEnabled || false,
        customCss: brandingData.customCss || '',
        logoUrl: brandingData.logoUrl,
      });
      if (brandingData.logoUrl) {
        setLogoPreview(brandingData.logoUrl);
      }
    }
  }

  const updateMutation = useMutation({
    mutationFn: (data: BrandingSettings) => incokalkAPI.branding.update(data),
    onSuccess: () => {
      toast.success('Configuration du portail sauvegardée');
      setHasChanges(false);
      queryClient.invalidateQueries({ queryKey: ['branding'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde');
    },
  });

  const logoMutation = useMutation({
    mutationFn: (file: File) => incokalkAPI.branding.uploadLogo(file),
    onSuccess: (res) => {
      toast.success('Logo téléversé avec succès');
      setLogoPreview(res.data?.logoUrl || null);
      setLogoFile(null);
      queryClient.invalidateQueries({ queryKey: ['branding'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du téléversement du logo');
    },
  });

  const handleLogoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setLogoFile(file);
    const reader = new FileReader();
    reader.onload = (ev) => {
      setLogoPreview(ev.target?.result as string);
    };
    reader.readAsDataURL(file);
  };

  const handleUploadLogo = () => {
    if (logoFile) {
      logoMutation.mutate(logoFile);
    }
  };

  const handleSave = () => {
    if (logoFile) {
      logoMutation.mutate(logoFile, {
        onSuccess: () => {
          updateMutation.mutate(settings);
        },
      });
    } else {
      updateMutation.mutate(settings);
    }
  };

  const handleReset = () => {
    if (brandingData) {
      setSettings({
        primaryColor: brandingData.primaryColor || DEFAULT_BRANDING.primaryColor,
        secondaryColor: brandingData.secondaryColor || DEFAULT_BRANDING.secondaryColor,
        portalTitle: brandingData.portalTitle || DEFAULT_BRANDING.portalTitle,
        welcomeMessage: brandingData.welcomeMessage || DEFAULT_BRANDING.welcomeMessage,
        footerText: brandingData.footerText || DEFAULT_BRANDING.footerText,
        customDomain: brandingData.customDomain || '',
        customCssEnabled: brandingData.customCssEnabled || false,
        customCss: brandingData.customCss || '',
        logoUrl: brandingData.logoUrl,
      });
      if (brandingData.logoUrl) {
        setLogoPreview(brandingData.logoUrl);
      } else {
        setLogoPreview(null);
      }
      setLogoFile(null);
      setHasChanges(false);
      toast.success('Modifications annulées');
    }
  };

  const updateField = <K extends keyof BrandingSettings>(key: K, value: BrandingSettings[K]) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
    setHasChanges(true);
  };

  const isSaving = updateMutation.isPending || logoMutation.isPending;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Portail White-Label
          </h1>
          <p className="text-ink-soft mt-1">Personnalisez l'apparence de votre portail client</p>
        </div>
        <div className="flex items-center gap-3">
          {hasChanges && (
            <button
              onClick={handleReset}
              disabled={isSaving}
              className="flex items-center gap-2 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg disabled:opacity-50 transition-colors"
            >
              <RefreshCw size={16} />
              Annuler
            </button>
          )}
          <button
            onClick={handleSave}
            disabled={isSaving}
            className="flex items-center gap-2 px-4 py-2 bg-terra-600 text-white rounded-none text-sm font-medium hover:bg-terra-700 disabled:opacity-50 transition-colors"
          >
            {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            {isSaving ? 'Sauvegarde...' : 'Sauvegarder'}
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          {/* Settings columns (3/5) */}
          <div className="lg:col-span-3 space-y-6">
            {/* Logo Upload */}
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Image size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">Logo de l'entreprise</h2>
              </div>
              <div className="px-6 py-4 space-y-4">
                <div className="flex items-center gap-6">
                  <div className="w-24 h-24 rounded-none border-2 border-dashed border-line flex items-center justify-center overflow-hidden bg-bg">
                    {logoPreview ? (
                      <img src={logoPreview} alt="Logo preview" className="w-full h-full object-contain" />
                    ) : (
                      <Image size={28} className="text-ink-soft" />
                    )}
                  </div>
                  <div className="flex-1">
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      onChange={handleLogoChange}
                      className="hidden"
                    />
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                    >
                      Choisir un fichier
                    </button>
                    <p className="text-xs text-ink-soft mt-2">PNG, JPG ou SVG. Max 2 Mo.</p>
                    {logoFile && (
                      <button
                        onClick={handleUploadLogo}
                        disabled={logoMutation.isPending}
                        className="mt-2 text-sm text-terra-600 hover:text-terra-700 font-medium disabled:opacity-50"
                      >
                        {logoMutation.isPending ? 'Téléversement...' : 'Téléverser le logo'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Colors */}
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Palette size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">Couleurs</h2>
              </div>
              <div className="px-6 py-4 grid grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-ink mb-2">Couleur primaire</label>
                  <div className="flex items-center gap-3">
                    <input
                      type="color"
                      value={settings.primaryColor}
                      onChange={(e) => updateField('primaryColor', e.target.value)}
                      className="w-10 h-10 rounded border border-line cursor-pointer p-0.5"
                    />
                    <input
                      type="text"
                      value={settings.primaryColor}
                      onChange={(e) => updateField('primaryColor', e.target.value)}
                      className="flex-1 px-3 py-2 border border-line rounded-none text-sm font-mono focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                      placeholder="#7c3aed"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-2">Couleur secondaire</label>
                  <div className="flex items-center gap-3">
                    <input
                      type="color"
                      value={settings.secondaryColor}
                      onChange={(e) => updateField('secondaryColor', e.target.value)}
                      className="w-10 h-10 rounded border border-line cursor-pointer p-0.5"
                    />
                    <input
                      type="text"
                      value={settings.secondaryColor}
                      onChange={(e) => updateField('secondaryColor', e.target.value)}
                      className="flex-1 px-3 py-2 border border-line rounded-none text-sm font-mono focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                      placeholder="#f59e0b"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Text content */}
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Type size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">Contenu textuel</h2>
              </div>
              <div className="px-6 py-4 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Titre du portail</label>
                  <input
                    type="text"
                    value={settings.portalTitle}
                    onChange={(e) => updateField('portalTitle', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="Portail Client"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Message de bienvenue</label>
                  <textarea
                    value={settings.welcomeMessage}
                    onChange={(e) => updateField('welcomeMessage', e.target.value)}
                    rows={3}
                    className="w-full px-3 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent resize-none"
                    placeholder="Bienvenue sur votre portail de suivi de livraisons"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Texte du pied de page</label>
                  <input
                    type="text"
                    value={settings.footerText}
                    onChange={(e) => updateField('footerText', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="© 2026 IncoKalk - Tous droits réservés"
                  />
                </div>
              </div>
            </div>

            {/* Custom Domain */}
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Globe size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">Domaine personnalisé</h2>
              </div>
              <div className="px-6 py-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Domaine (optionnel)</label>
                  <input
                    type="text"
                    value={settings.customDomain || ''}
                    onChange={(e) => updateField('customDomain', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="portail.monentreprise.com"
                  />
                  <p className="text-xs text-ink-soft mt-1">
                    Configurez votre enregistrement DNS CNAME pointant vers votre portail IncoKalk.
                  </p>
                </div>
              </div>
            </div>

            {/* Custom CSS */}
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Eye size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">CSS personnalisé</h2>
              </div>
              <div className="px-6 py-4 space-y-4">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={settings.customCssEnabled}
                    onChange={(e) => updateField('customCssEnabled', e.target.checked)}
                    className="w-4 h-4 text-terra-600 border-line rounded focus:ring-terra-500"
                  />
                  <span className="text-sm text-ink">Activer le CSS personnalisé</span>
                </label>
                {settings.customCssEnabled && (
                  <textarea
                    value={settings.customCss || ''}
                    onChange={(e) => updateField('customCss', e.target.value)}
                    rows={8}
                    className="w-full px-3 py-2 border border-line rounded-none text-sm font-mono focus:ring-2 focus:ring-terra-500 focus:border-transparent resize-none"
                    placeholder="/* Vos styles CSS personnalisés */"
                  />
                )}
              </div>
            </div>
          </div>

          {/* Preview column (2/5) */}
          <div className="lg:col-span-2">
            <div className="relative bg-surface rounded-none border border-line overflow-hidden sticky top-8">
              <span className="hud-corner hud-corner-tl" aria-hidden="true" />
              <span className="hud-corner hud-corner-tr" aria-hidden="true" />
              <span className="hud-corner hud-corner-bl" aria-hidden="true" />
              <span className="hud-corner hud-corner-br" aria-hidden="true" />
              <div className="px-6 py-4 border-b border-line flex items-center gap-2">
                <Eye size={18} className="text-terra-500" />
                <h2 className="text-lg font-semibold text-ink">Aperçu</h2>
              </div>
              <div className="p-6">
                <div
                  className="rounded-none overflow-hidden border shadow-lg"
                  style={{ borderColor: settings.primaryColor }}
                >
                  {/* Mock header */}
                  <div
                    className="px-6 py-5 flex items-center gap-3"
                    style={{ backgroundColor: settings.primaryColor }}
                  >
                    <div className="w-8 h-8 rounded-none bg-surface/20 flex items-center justify-center text-white font-bold text-xs">
                      {logoPreview ? (
                        <img src={logoPreview} alt="" className="w-full h-full object-contain" />
                      ) : (
                        'IK'
                      )}
                    </div>
                    <span className="text-white font-semibold text-sm">
                      {settings.portalTitle || 'Portail Client'}
                    </span>
                  </div>

                  {/* Mock body */}
                  <div className="p-6 space-y-4 bg-surface">
                    <h3
                      className="text-lg font-bold"
                      style={{ color: settings.primaryColor }}
                    >
                      {settings.welcomeMessage || 'Bienvenue sur votre portail'}
                    </h3>

                    {/* Mock cards */}
                    <div className="grid grid-cols-2 gap-3">
                      <div
                        className="p-3 rounded-none"
                        style={{ backgroundColor: settings.primaryColor + '10', borderLeft: `3px solid ${settings.primaryColor}` }}
                      >
                        <p className="text-xs font-semibold" style={{ color: settings.primaryColor }}>Expéditions</p>
                        <p className="text-2xl font-bold text-ink">12</p>
                      </div>
                      <div
                        className="p-3 rounded-none"
                        style={{ backgroundColor: settings.secondaryColor + '10', borderLeft: `3px solid ${settings.secondaryColor}` }}
                      >
                        <p className="text-xs font-semibold" style={{ color: settings.secondaryColor }}>En transit</p>
                        <p className="text-2xl font-bold text-ink">5</p>
                      </div>
                    </div>

                    {/* Mock list item */}
                    <div className="flex items-center gap-3 p-3 rounded-none border border-line">
                      <div
                        className="w-2 h-2 rounded-full"
                        style={{ backgroundColor: settings.primaryColor }}
                      />
                      <div className="flex-1">
                        <p className="text-xs font-medium text-ink">EXP-2026-0042</p>
                        <p className="text-xs text-ink-soft">Paris → New York</p>
                      </div>
                      <span
                        className="text-xs font-medium px-2 py-0.5 rounded-full"
                        style={{
                          backgroundColor: settings.secondaryColor + '20',
                          color: settings.secondaryColor,
                        }}
                      >
                        En cours
                      </span>
                    </div>
                    <div className="flex items-center gap-3 p-3 rounded-none border border-line">
                      <div
                        className="w-2 h-2 rounded-full"
                        style={{ backgroundColor: settings.secondaryColor }}
                      />
                      <div className="flex-1">
                        <p className="text-xs font-medium text-ink">EXP-2026-0041</p>
                        <p className="text-xs text-ink-soft">Lyon → Berlin</p>
                      </div>
                      <span
                        className="text-xs font-medium px-2 py-0.5 rounded-full"
                        style={{
                          backgroundColor: settings.secondaryColor + '20',
                          color: settings.secondaryColor,
                        }}
                      >
                        Livré
                      </span>
                    </div>
                  </div>

                  {/* Mock footer */}
                  <div
                    className="px-6 py-3 text-center text-xs"
                    style={{
                      backgroundColor: settings.primaryColor + '08',
                      color: settings.primaryColor,
                    }}
                  >
                    {settings.footerText || '© 2026 IncoKalk'}
                  </div>
                </div>

                <p className="text-xs text-ink-soft mt-3 text-center">
                  Aperçu dynamique basé sur vos paramètres actuels
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BrandingPortal;
