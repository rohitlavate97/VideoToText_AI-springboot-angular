import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'videos',
    loadComponent: () => import('./features/videos/library/video-library.component').then(m => m.VideoLibraryComponent),
    canActivate: [authGuard]
  },
  {
    path: 'videos/upload',
    loadComponent: () => import('./features/videos/upload/video-upload.component').then(m => m.VideoUploadComponent),
    canActivate: [authGuard]
  },
  {
    path: 'videos/:id',
    loadComponent: () => import('./features/videos/detail/video-detail.component').then(m => m.VideoDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent),
    canActivate: [authGuard]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
