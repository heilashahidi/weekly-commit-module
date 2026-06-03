Feature: Walking skeleton health check

  The Weekly Commit remote loads standalone and renders a health state from the
  backend, proving the end-to-end wiring (remote → RTK Query → backend).

  Scenario: The app loads and renders a backend health state
    Given the Weekly Commit app is open
    Then the app title is visible
    And a backend health state is shown
