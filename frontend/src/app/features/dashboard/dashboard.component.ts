import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardStats } from '../../core/models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatChipsModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  readonly authService = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);

  currentUser$ = this.authService.currentUser$;
  stats: DashboardStats | null = null;
  isLoadingStats = true;

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.isLoadingStats = true;
    this.dashboardService.getStats().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.stats = res.data;
        }
        this.isLoadingStats = false;
      },
      error: () => {
        this.isLoadingStats = false;
      }
    });
  }

  formatDuration(totalSeconds: number): string {
    if (!totalSeconds || totalSeconds <= 0) return '0m';
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }

  formatNumber(num: number): string {
    return (num || 0).toLocaleString();
  }

  getLanguageName(code: string): string {
    const map: { [key: string]: string } = {
      en: 'English',
      hi: 'Hindi (हिन्दी)',
      mr: 'Marathi (मराठी)',
      es: 'Spanish',
      fr: 'French',
      de: 'German'
    };
    return map[code.toLowerCase()] || code.toUpperCase();
  }

  getLanguagePercentage(count: number): number {
    if (!this.stats || !this.stats.languageDistribution) return 0;
    const total = Object.values(this.stats.languageDistribution).reduce((a, b) => a + b, 0);
    if (total === 0) return 0;
    return Math.round((count / total) * 100);
  }

  getLanguageEntries(): { code: string; count: number; percentage: number }[] {
    if (!this.stats || !this.stats.languageDistribution) return [];
    return Object.entries(this.stats.languageDistribution).map(([code, count]) => ({
      code,
      count,
      percentage: this.getLanguagePercentage(count)
    }));
  }

  onLogout(): void {
    this.authService.logout();
  }
}
