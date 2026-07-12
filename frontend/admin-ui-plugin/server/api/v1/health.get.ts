// Simple health check that returns the BFF status.
// Real proxy routes should call the Spring Boot API from server/utils.
export default defineEventHandler(() => {
  return { status: 'ok', bff: 'admin-ui-plugin' }
})
