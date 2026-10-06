<script setup lang="ts">
useHead({
  titleTemplate: (title) => (title ? `${title} — CBS Operator Portal` : 'CBS Operator Portal'),
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
