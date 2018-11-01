import { Injectable } from '@angular/core';
import { AuthenticationService, Configuration } from 'src/oversigt-client';



@Injectable({
  providedIn: 'root'
})
export class UserServiceService {
  private _name: string = null;
  private _token: string = null;
  private _roles: string[] = [];

  constructor(
    private authorization: AuthenticationService,
    private configuration: Configuration,
  ) { }

  get loggedIn(): boolean {
    return this._token !== null;
  }

  get name(): string {
    return this._name;
  }

  public logIn(username: string, password: string, success: (name: string) => void, fail: () => void, done: () => void): void {
    this.authorization.authenticateUser(username, password).subscribe(
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
      () => {
        done();
      }
    );
  }

  hasRole(roleName: string): boolean {
    return this._roles.includes(roleName);
  }
}
