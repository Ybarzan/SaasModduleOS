import type { AxiosError, AxiosResponse } from 'axios';
import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Building2, Save, Globe, Phone, Mail, MapPin, Image, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface CompanySettingsData {
  name: string;
  legalName: string;
  siret: string;
  vatNumber: string;
  address: string;
  city: string;
  postalCode: string;
  country: string;
  phone: string;
  email: string;
  website: string;
  logoUrl?: string;
}

const DEFAULT_SETTINGS: CompanySettingsData = {
  name: '',
  legalName: '',
  siret: '',
  vatNumber: '',
  address: '',
  city: '',
  postalCode: '',
  country: '',
  phone: '',
  email: '',
  website: '',
  logoUrl: '',
};

const CompanySettings = () => {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [settings, setSettings] = useState<CompanySettingsData>(DEFAULT_SETTINGS);
  const [hasChanges, setHasChanges] = useState(false);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);

  const { data: companyData, isLoading } = useQuery({
    queryKey: ['company-settings'],
    queryFn: async () => {
      const res = await incokalkAPI.branding.get();
      return res.data as CompanySettingsData;
    },
  });

  const [prevCompanyData, setPrevCompanyData] = useState(companyData);
  if (companyData !== prevCompanyData) {
    setPrevCompanyData(companyData);
    if (companyData) {
      setSettings({
        name: companyData.name || '',
        legalName: companyData.legalName || '',
        siret: companyData.siret || '',
        vatNumber: companyData.vatNumber || '',
        address: companyData.address || '',
        city: companyData.city || '',
        postalCode: companyData.postalCode || '',
        country: companyData.country || '',
        phone: companyData.phone || '',
        email: companyData.email || '',
        website: companyData.website || '',
        logoUrl: companyData.logoUrl || '',
      });
      if (companyData.logoUrl) {
        setLogoPreview(companyData.logoUrl);
      }
    }
  }

  const updateMutation = useMutation({
    mutationFn: (data: CompanySettingsData) => incokalkAPI.branding.update(data),
    onSuccess: () => {
      toast.success('Paramètres entreprise sauvegardés');
      setHasChanges(false);
      queryClient.invalidateQueries({ queryKey: ['company-settings'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde');
    },
  });

  const logoMutation = useMutation({
    mutationFn: (file: File) => incokalkAPI.branding.uploadLogo(file),
    onSuccess: (res: AxiosResponse<{ logoUrl?: string }>) => {
      toast.success('Logo téléversé avec succès');
      setLogoPreview(res.data?.logoUrl || null);
      setLogoFile(null);
      queryClient.invalidateQueries({ queryKey: ['company-settings'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du téléversement du logo');
    },
  });

  const updateField = <K extends keyof CompanySettingsData>(key: K, value: CompanySettingsData[K]) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
    setHasChanges(true);
  };

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

  const isSaving = updateMutation.isPending || logoMutation.isPending;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Paramètres entreprise</h1>
          <p className="text-ink-soft mt-1">Informations légales et coordonnées de votre entreprise</p>
        </div>
        <button
          onClick={handleSave}
          disabled={isSaving || !hasChanges}
          className="flex items-center gap-2 px-4 py-2 bg-terra-600 text-white rounded-lg font-medium hover:bg-terra-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
          {isSaving ? 'Sauvegarde...' : 'Sauvegarder'}
        </button>
      </div>

      {isLoading ? (
        <div className="bg-surface rounded-xl border border-line px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : (
        <div className="space-y-6">
          {/* Logo */}
          <div className="bg-surface rounded-xl border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line flex items-center gap-2">
              <Image size={18} className="text-terra-500" />
              <h2 className="text-lg font-semibold text-ink">Logo</h2>
            </div>
            <div className="px-6 py-4">
              <div className="flex items-center gap-6">
                <div className="w-24 h-24 rounded-xl border-2 border-dashed border-line flex items-center justify-center overflow-hidden bg-bg">
                  {logoPreview ? (
                    <img src={logoPreview} alt="Logo" className="w-full h-full object-contain" />
                  ) : (
                    <Building2 size={28} className="text-ink-soft" />
                  )}
                </div>
                <div>
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
                    className="px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                  >
                    Choisir un fichier
                  </button>
                  <p className="text-xs text-ink-soft mt-2">PNG, JPG ou SVG. Max 2 Mo.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Company Info */}
          <div className="bg-surface rounded-xl border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line flex items-center gap-2">
              <Building2 size={18} className="text-terra-500" />
              <h2 className="text-lg font-semibold text-ink">Informations légales</h2>
            </div>
            <div className="px-6 py-4 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom commercial</label>
                  <input
                    type="text"
                    value={settings.name}
                    onChange={(e) => updateField('name', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="IncoKalk SAS"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Raison sociale</label>
                  <input
                    type="text"
                    value={settings.legalName}
                    onChange={(e) => updateField('legalName', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="IncoKalk Société par Actions Simplifiée"
                  />
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">SIRET / SIREN</label>
                  <input
                    type="text"
                    value={settings.siret}
                    onChange={(e) => updateField('siret', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="123 456 789 00012"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Numéro de TVA</label>
                  <input
                    type="text"
                    value={settings.vatNumber}
                    onChange={(e) => updateField('vatNumber', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="FR12345678901"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Address */}
          <div className="bg-surface rounded-xl border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line flex items-center gap-2">
              <MapPin size={18} className="text-terra-500" />
              <h2 className="text-lg font-semibold text-ink">Adresse</h2>
            </div>
            <div className="px-6 py-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Adresse</label>
                <input
                  type="text"
                  value={settings.address}
                  onChange={(e) => updateField('address', e.target.value)}
                  className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                  placeholder="123 Rue de l'Exemple"
                />
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Ville</label>
                  <input
                    type="text"
                    value={settings.city}
                    onChange={(e) => updateField('city', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="Paris"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Code postal</label>
                  <input
                    type="text"
                    value={settings.postalCode}
                    onChange={(e) => updateField('postalCode', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="75001"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Pays</label>
                  <input
                    type="text"
                    value={settings.country}
                    onChange={(e) => updateField('country', e.target.value)}
                    className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="France"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Contact */}
          <div className="bg-surface rounded-xl border border-line overflow-hidden">
            <div className="px-6 py-4 border-b border-line flex items-center gap-2">
              <Phone size={18} className="text-terra-500" />
              <h2 className="text-lg font-semibold text-ink">Contact</h2>
            </div>
            <div className="px-6 py-4 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Téléphone</label>
                  <div className="relative">
                    <Phone size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                    <input
                      type="tel"
                      value={settings.phone}
                      onChange={(e) => updateField('phone', e.target.value)}
                      className="w-full pl-10 pr-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                      placeholder="+33 1 23 45 67 89"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Email</label>
                  <div className="relative">
                    <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                    <input
                      type="email"
                      value={settings.email}
                      onChange={(e) => updateField('email', e.target.value)}
                      className="w-full pl-10 pr-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                      placeholder="contact@entreprise.com"
                    />
                  </div>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Site web</label>
                <div className="relative">
                  <Globe size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                  <input
                    type="url"
                    value={settings.website}
                    onChange={(e) => updateField('website', e.target.value)}
                    className="w-full pl-10 pr-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-terra-500 focus:border-transparent"
                    placeholder="https://www.entreprise.com"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Save button (mobile sticky) */}
          <div className="sticky bottom-4 bg-surface border border-line rounded-xl p-4 shadow-lg flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-ink">Modifications non sauvegardées</p>
              <p className="text-xs text-ink-soft">Les changements seront appliqués après sauvegarde</p>
            </div>
            <button
              onClick={handleSave}
              disabled={isSaving || !hasChanges}
              className="flex items-center gap-2 px-6 py-2 bg-terra-600 text-white rounded-lg font-medium hover:bg-terra-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
              {isSaving ? 'Sauvegarde...' : 'Sauvegarder'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default CompanySettings;
