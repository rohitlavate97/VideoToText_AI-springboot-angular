import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';
import { Router, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../../../core/models/user.model';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-token',
    refreshToken: 'test-refresh',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'uuid-1',
      email: 'test@example.com',
      firstName: 'Test',
      lastName: 'User',
      roles: ['ROLE_USER']
    }
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the login component', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with invalid empty form', () => {
    expect(component.loginForm.valid).toBeFalse();
    expect(component.loginForm.get('email')?.value).toBe('');
    expect(component.loginForm.get('password')?.value).toBe('');
  });

  it('should validate email format properly', () => {
    const emailControl = component.loginForm.get('email');
    emailControl?.setValue('invalid-email');
    expect(emailControl?.hasError('email')).toBeTrue();

    emailControl?.setValue('valid@example.com');
    expect(emailControl?.hasError('email')).toBeFalse();
  });

  it('should call authService.login and navigate to /dashboard on valid submission', () => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.loginForm.setValue({
      email: 'valid@example.com',
      password: 'Password123!'
    });

    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'valid@example.com',
      password: 'Password123!'
    });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.isLoading).toBeFalse();
  });

  it('should display error message on login failure', () => {
    authServiceSpy.login.and.returnValue(
      throwError(() => ({ error: { error: { message: 'Invalid credentials' } } }))
    );

    component.loginForm.setValue({
      email: 'valid@example.com',
      password: 'WrongPassword!'
    });

    component.onSubmit();

    expect(component.errorMessage).toBe('Invalid credentials');
    expect(component.isLoading).toBeFalse();
  });
});
