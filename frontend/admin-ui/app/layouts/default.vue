<template>
  <div class="flex h-screen overflow-hidden bg-gray-50">
    <AppSidebar />
    <AppMobileDrawer />
    <div class="flex flex-col flex-1 overflow-hidden">
      <AppTopBar />
      <main class="flex-1 overflow-y-auto p-6">
        <slot />
      </main>
    </div>
  </div>
</template>