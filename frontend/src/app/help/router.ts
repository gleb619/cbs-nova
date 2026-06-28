import HelpPage from './HelpPage.vue';
import PrivacyPolicy from './PrivacyPolicy.vue';
import TermsOfService from './TermsOfService.vue';

export const helpRoutes = () => [
  { path: '/help', name: 'help', component: HelpPage },
  { path: '/privacy', name: 'privacy', component: PrivacyPolicy },
  { path: '/terms', name: 'terms', component: TermsOfService },
];
