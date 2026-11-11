import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductCounter from '../components/ProductCounter'
import ActivityPortal from '../pages/ActivityPortal'
import {
  chosenActivity,
  isMedical,
  rememberActivity,
  usesMarketplace,
  usesTachograph
} from '../fleet'
import { cleanProducts, describeProducts, parseProducts } from '../medical'

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))

vi.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: null, search: '' })
}))

function Counter({ initial = {}, expected }) {
  const [value, setValue] = useState(initial)
  return <ProductCounter value={value} onChange={setValue} expected={expected} />
}

describe('Activités', () => {
  beforeEach(() => {
    localStorage.clear()
    mockNavigate.mockReset()
  })

  it('le portail propose les trois activités et ouvre la connexion de celle choisie', async () => {
    const user = userEvent.setup()
    render(<ActivityPortal />)

    expect(screen.getByText('Transport poids lourds')).toBeInTheDocument()
    expect(screen.getByText('Transport messagerie')).toBeInTheDocument()
    expect(screen.getByText('Transport médical')).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: 'Se connecter' })[2])
    expect(mockNavigate).toHaveBeenCalledWith('/login?activite=medical', { state: null })
    // Mémorisée pour le prochain passage
    expect(chosenActivity('').value).toBe('COLLECTE_SANTE')
  })

  it("l'activité de l'URL prime sur celle mémorisée", () => {
    rememberActivity('poids-lourd')
    expect(chosenActivity('?activite=messagerie').value).toBe('MESSAGERIE')
    expect(chosenActivity('?activite=inconnue').value).toBe('POIDS_LOURD')
  })

  it("l'espace médical est cloisonné", () => {
    const medical = { fleetProfile: 'COLLECTE_SANTE' }
    expect(isMedical(medical)).toBe(true)
    expect(usesTachograph(medical)).toBe(false)
    expect(usesMarketplace(medical)).toBe(false)
    expect(isMedical({ fleetProfile: 'MIXTE' })).toBe(false)
  })
})

describe('Comptage des produits', () => {
  it('compte par catégorie et calcule le total', async () => {
    const user = userEvent.setup()
    render(<Counter />)

    await user.click(screen.getByRole('button', { name: 'Sang plus un' }))
    await user.click(screen.getByRole('button', { name: 'Sang plus un' }))
    await user.click(screen.getByRole('button', { name: 'PCR / écouvillons plus un' }))

    expect(screen.getByLabelText('Quantité Sang')).toHaveValue(2)
    expect(screen.getByText('Total').querySelector('b')).toHaveTextContent('3')
    // Pas de quantité négative
    expect(screen.getByRole('button', { name: 'Urines moins un' })).toBeDisabled()
  })

  it('signale un écart avec les produits attendus à la remise', async () => {
    const user = userEvent.setup()
    render(<Counter initial={{ SANG: 4, PCR: 2 }} expected={{ SANG: 4, PCR: 2 }} />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'PCR / écouvillons moins un' }))
    expect(screen.getByRole('status')).toHaveTextContent('Écart avec les produits pris en charge')
  })

  it('formate et relit les quantités du journal de garde', () => {
    expect(parseProducts('SANG=3,PCR=2')).toEqual({ SANG: 3, PCR: 2 })
    expect(describeProducts({ SANG: 3, URINE: 0, PCR: 2 })).toBe('Sang 3 · PCR 2')
    expect(describeProducts({})).toBe('aucun produit')
    expect(cleanProducts({ SANG: 0, PCR: 1 })).toEqual({ PCR: 1 })
  })
})
