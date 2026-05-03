import HelpPageVue from '../infrastructure/primary/HelpPageVue.vue';
import PrivacyPolicyVue from '../infrastructure/primary/PrivacyPolicyVue.vue';
import TermsOfServiceVue from '../infrastructure/primary/TermsOfServiceVue.vue';

export const helpRoutes = () => [
  {
    path: '/help',
    name: 'help',
    component: HelpPageVue,
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: PrivacyPolicyVue,
  },
  {
    path: '/terms',
    name: 'terms',
    component: TermsOfServiceVue,
  },
];
