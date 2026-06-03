import { Given, Then } from '@badeball/cypress-cucumber-preprocessor';

Given('the Weekly Commit app is open', () => {
  cy.visit('/');
});

Then('the app title is visible', () => {
  cy.contains('h1', 'Weekly Commit').should('be.visible');
});

Then('a backend health state is shown', () => {
  // With no backend running the query resolves to the error state; with one it
  // shows the status. Either proves the walking-skeleton route rendered.
  cy.contains(/Backend status:|Backend unavailable|Checking backend/).should('exist');
});
