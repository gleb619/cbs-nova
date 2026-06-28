let localAuthMode = false;

export const setAuthConfig = (localAuth: boolean): void => {
  localAuthMode = localAuth;
};

export const isLocalAuthMode = (): boolean => localAuthMode;

export const initializeAuthConfig = (localAuth: boolean): void => {
  localAuthMode = localAuth;
};
