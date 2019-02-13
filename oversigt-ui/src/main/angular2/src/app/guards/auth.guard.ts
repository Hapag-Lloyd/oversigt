import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router, CanActivateChild } from '@angular/router';
import { Observable } from 'rxjs';
import { UserService } from '../services/user-service.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild {
  constructor(
    private userService: UserService,
    private router: Router,
  ) { }

  canActivate(
      next: ActivatedRouteSnapshot,
      state: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean {
    const isLoggedIn = this.userService.isLoggedIn();
    if (typeof isLoggedIn === 'boolean') {
      if (!isLoggedIn) {
        this.userService.requestedUrl = state.url;
        this.router.navigateByUrl('/login');
        return false;
      }
      return true;
    } else {
      return new Promise((resolve, reject) => {
        isLoggedIn.subscribe(ok => {
          if (!ok) {
            this.router.navigateByUrl('/login');
          }
          resolve(ok);
        });
      });
    }
  }

  canActivateChild(childRoute: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Observable<boolean> | Promise<boolean> {
    return this.canActivate(childRoute, state);
  }
}
