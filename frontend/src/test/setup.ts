import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'

// The full suite runs jsdom files in parallel; one second is too tight for findBy* under that load.
configure({ asyncUtilTimeout: 3000 })

// Routing reads the real jsdom location, so every test starts from the root path.
beforeEach(() => {
  window.history.replaceState(null, '', '/')
})

afterEach(() => {
  cleanup()
})

// jsdom does not implement the browser's top layer. Real focus/inert behavior is checked in E2E.
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
}
