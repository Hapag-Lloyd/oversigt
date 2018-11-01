import { Injectable } from '@angular/core';
import { AuthenticationService, Configuration } from 'src/oversigt-client';
import { UrlSegment } from '@angular/router';



@Injectable({
  providedIn: 'root'
})
export class UserService {
  private _name: string = null;
  private _token: string = null;
  private _roles: string[] = [];

  private _requestedUrl: string = null;

  constructor(
    private authentication: AuthenticationService,
    private configuration: Configuration,
  ) { }

  get isLoggedIn(): boolean {
    return this._token !== null;
  }

  get name(): string {
    return this._name;
  }

  get requestedUrl(): string {
    if (this._requestedUrl === null || this._requestedUrl.length === 0) {
      return 'config';
    } else {
      return this._requestedUrl;
    }
  }

  set requestedUrl(url: string) {
    this._requestedUrl = url;
  }

  public logIn(username: string, password: string, success: (name: string) => void, fail: () => void, done: () => void): void {
    this.authentication.authenticateUser(username, password).subscribe(
      ok => {
        const authorization = 'Bearer ' + ok.token;
        this._token = ok.token;
        this._name = ok.displayName;
        this._roles = ok.roles;
        this.configuration.apiKeys['Authorization'] = authorization;
        success(ok.displayName);
      },
      error => {
        if (error.status === 403) {
          fail();
        } else {
          console.error(error);
          // TODO
        }
      },
      done
    );
  }

  hasRole(roleName: string): boolean {
    return this._roles.includes(roleName);
  }
}
