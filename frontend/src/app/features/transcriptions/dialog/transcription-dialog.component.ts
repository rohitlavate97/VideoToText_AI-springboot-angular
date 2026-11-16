import { Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { CreateTranscriptionRequest } from '../../../core/models/transcription.model';

export interface TranscriptionDialogData {
  videoId: string;
  videoTitle: string;
}

@Component({
  selector: 'app-transcription-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatIconModule
  ],
  template: `
    <div class="dialog-container">
      <div class="dialog-header">
        <div class="title-row">
          <mat-icon class="ai-icon">auto_awesome</mat-icon>
          <h2 mat-dialog-title>Configure Speech-to-Text Transcription</h2>
        </div>
        <p class="subtitle">Video: <strong>{{ data.videoTitle }}</strong></p>
      </div>

      <mat-dialog-content>
        <form [formGroup]="form" class="config-form">
          <div class="form-grid">
            <!-- Source Language -->
            <mat-form-field appearance="outline">
              <mat-label>Spoken Language</mat-label>
              <mat-select formControlName="language">
                <mat-option value="auto">Auto-detect Language</mat-option>
                <mat-option value="en">English (en)</mat-option>
                <mat-option value="es">Spanish (es)</mat-option>
                <mat-option value="fr">French (fr)</mat-option>
                <mat-option value="de">German (de)</mat-option>
                <mat-option value="it">Italian (it)</mat-option>
                <mat-option value="pt">Portuguese (pt)</mat-option>
                <mat-option value="ja">Japanese (ja)</mat-option>
                <mat-option value="zh">Chinese (zh)</mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Model Size -->
            <mat-form-field appearance="outline">
              <mat-label>Whisper Model</mat-label>
              <mat-select formControlName="model">
                <mat-option value="tiny">Tiny (Fastest, lower VRAM)</mat-option>
                <mat-option value="base">Base (Fast, balanced)</mat-option>
                <mat-option value="small">Small (Recommended, high accuracy)</mat-option>
                <mat-option value="medium">Medium (Very high accuracy)</mat-option>
                <mat-option value="large-v3">Large-v3 (Maximum precision)</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <!-- Feature Toggles -->
          <div class="toggle-group">
            <div class="toggle-card">
              <div class="toggle-info">
                <mat-icon>record_voice_over</mat-icon>
                <div>
                  <strong>Speaker Diarization</strong>
                  <p>Identify and distinguish distinct speaker turns using PyAnnote.</p>
                </div>
              </div>
              <mat-slide-toggle formControlName="enableDiarization" color="primary"></mat-slide-toggle>
            </div>

            <div class="toggle-card">
              <div class="toggle-info">
                <mat-icon>summarize</mat-icon>
                <div>
                  <strong>AI Executive Summary</strong>
                  <p>Generate key bullet points and high-level overview.</p>
                </div>
              </div>
              <mat-slide-toggle formControlName="generateSummary" color="primary"></mat-slide-toggle>
            </div>
          </div>

          <!-- Target Languages -->
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Multi-Language Translation (Optional)</mat-label>
            <mat-select formControlName="targetLanguages" multiple>
              <mat-option value="es">Spanish</mat-option>
              <mat-option value="fr">French</mat-option>
              <mat-option value="de">German</mat-option>
              <mat-option value="it">Italian</mat-option>
              <mat-option value="pt">Portuguese</mat-option>
              <mat-option value="ja">Japanese</mat-option>
              <mat-option value="zh">Chinese</mat-option>
            </mat-select>
          </mat-form-field>
        </form>
      </mat-dialog-content>

      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-raised-button color="primary" (click)="submit()">
          <mat-icon>play_arrow</mat-icon>
          Start Transcription
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .dialog-container {
      padding: 16px 20px;
      max-width: 580px;
    }
    .dialog-header {
      margin-bottom: 20px;
      .title-row {
        display: flex;
        align-items: center;
        gap: 10px;
        .ai-icon {
          color: #7c4dff;
          font-size: 28px;
          width: 28px;
          height: 28px;
        }
        h2 {
          margin: 0;
          font-size: 1.25rem;
          font-weight: 600;
        }
      }
      .subtitle {
        margin: 6px 0 0 38px;
        font-size: 0.85rem;
        color: #718096;
      }
    }
    .config-form {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    .toggle-group {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin: 8px 0;
    }
    .toggle-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      .toggle-info {
        display: flex;
        align-items: center;
        gap: 12px;
        mat-icon {
          color: #4f46e5;
        }
        p {
          margin: 0;
          font-size: 0.75rem;
          color: #64748b;
        }
        strong {
          font-size: 0.9rem;
          color: #1e293b;
        }
      }
    }
    .full-width {
      width: 100%;
    }
    .dialog-actions {
      padding: 16px 0 0;
      gap: 8px;
    }
  `]
})
export class TranscriptionDialogComponent {
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject(MatDialogRef<TranscriptionDialogComponent>);

  constructor(@Inject(MAT_DIALOG_DATA) public data: TranscriptionDialogData) {}

  form: FormGroup = this.fb.group({
    language: ['auto'],
    engine: ['faster-whisper'],
    model: ['small'],
    computeType: ['int8_float16'],
    enableDiarization: [false],
    generateSummary: [false],
    targetLanguages: [[]]
  });

  submit(): void {
    const val = this.form.value;
    const req: CreateTranscriptionRequest = {
      videoId: this.data.videoId,
      language: val.language,
      engine: val.engine,
      model: val.model,
      computeType: val.computeType,
      enableDiarization: val.enableDiarization,
      generateSummary: val.generateSummary,
      targetLanguages: val.targetLanguages
    };
    this.dialogRef.close(req);
  }
}
