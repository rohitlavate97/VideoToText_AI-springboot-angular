import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { UserService } from '../../core/services/user.service';
import { UserProfile } from '../../core/models/user.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatChipsModule
  ],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  profile: UserProfile | null = null;
  isLoading = true;
  isSavingProfile = false;
  isSavingPreferences = false;

  profileForm: FormGroup = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]]
  });

  preferencesForm: FormGroup = this.fb.group({
    defaultLanguage: ['en', [Validators.required]],
    processingMode: ['local', [Validators.required]],
    preferredAsrEngine: ['faster-whisper', [Validators.required]],
    preferredAsrModel: ['small', [Validators.required]]
  });

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.isLoading = true;
    this.userService.getProfile().subscribe({
      next: (profile) => {
        this.profile = profile;
        this.profileForm.patchValue({
          firstName: profile.firstName,
          lastName: profile.lastName
        });
        if (profile.preferences) {
          this.preferencesForm.patchValue({
            defaultLanguage: profile.preferences.defaultLanguage || 'en',
            processingMode: profile.preferences.processingMode || 'local',
            preferredAsrEngine: profile.preferences.preferredAsrEngine || 'faster-whisper',
            preferredAsrModel: profile.preferences.preferredAsrModel || 'small'
          });
        }
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.snackBar.open('Failed to load profile. Please try again.', 'Close', { duration: 4000 });
      }
    });
  }

  saveProfile(): void {
    if (this.profileForm.invalid) return;

    this.isSavingProfile = true;
    this.userService.updateProfile(this.profileForm.value).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.isSavingProfile = false;
        this.snackBar.open('Profile details updated successfully', 'Close', { duration: 3000 });
      },
      error: () => {
        this.isSavingProfile = false;
        this.snackBar.open('Failed to update profile.', 'Close', { duration: 4000 });
      }
    });
  }

  savePreferences(): void {
    if (this.preferencesForm.invalid) return;

    this.isSavingPreferences = true;
    this.userService.updatePreferences(this.preferencesForm.value).subscribe({
      next: () => {
        this.isSavingPreferences = false;
        this.snackBar.open('Preferences saved successfully', 'Close', { duration: 3000 });
      },
      error: () => {
        this.isSavingPreferences = false;
        this.snackBar.open('Failed to update preferences.', 'Close', { duration: 4000 });
      }
    });
  }
}
